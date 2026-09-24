package dev.changoff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import static dev.changoff.WodModels.*;

@Service
public class WodService {
    private final JdbcTemplate db; private final WodCatalog catalog; private final ObjectMapper json;
    public WodService(JdbcTemplate db,WodCatalog catalog,ObjectMapper json) {this.db=db;this.catalog=catalog;this.json=json;}
    String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    <T> T decode(String text,Class<T> type) {try{return json.readValue(text,type);}catch(Exception e){throw new IllegalStateException(e);}}
    static ResponseStatusException conflict(String message) {return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    static void require(boolean valid,String message) {if(!valid)throw new IllegalArgumentException(message);}
    Set<String> ids(String table) {return new HashSet<>(db.queryForList("SELECT id FROM "+table,String.class));}
    void validIds(Set<String> given,String table) {require(ids(table).containsAll(given),"Unknown selection for "+table+".");}
    public Preferences preferences(UUID user) {
        int level=db.queryForObject("SELECT wod_level_id FROM athletes WHERE id=?",Integer.class,user);
        boolean configured=db.queryForObject("SELECT COUNT(*) FROM user_wod_preference WHERE user_id=?",Integer.class,user)>0;
        Set<String> equipment=configured?new HashSet<>(db.queryForList("SELECT equipment_id FROM user_equipment_preference WHERE user_id=?",String.class,user)):ids("equipment");
        List<Restriction> restrictions=db.query("SELECT * FROM user_restriction WHERE user_id=? ORDER BY id",(r,n)->{
            for(String kind:List.of("exercise","pattern","equipment","muscle"))if(r.getString(kind+"_id")!=null)return new Restriction(kind,r.getString(kind+"_id"),r.getString("reason"),r.getBoolean("active"));
            throw new IllegalStateException("Invalid stored restriction");
        },user);
        return new Preferences(level,equipment,restrictions);
    }
    @Transactional public Preferences savePreferences(UUID user,Preferences p) {
        validIds(p.equipment(),"equipment");
        for(var r:p.restrictions())validIds(Set.of(r.target()),switch(r.kind()){case "exercise"->"wod_exercise";case "pattern"->"movement_pattern";case "muscle"->"muscle_group";default->"equipment";});
        db.queryForObject("SELECT id FROM athletes WHERE id=? FOR UPDATE",UUID.class,user);
        db.update("UPDATE athletes SET wod_level_id=? WHERE id=?",p.level(),user);
        if(db.queryForObject("SELECT COUNT(*) FROM user_wod_preference WHERE user_id=?",Integer.class,user)==0)db.update("INSERT INTO user_wod_preference VALUES(?)",user);
        db.update("DELETE FROM user_equipment_preference WHERE user_id=?",user);
        for(String e:p.equipment())db.update("INSERT INTO user_equipment_preference VALUES(?,?)",user,e);
        db.update("DELETE FROM user_restriction WHERE user_id=?",user);
        for(var r:p.restrictions())db.update("INSERT INTO user_restriction(id,user_id,"+r.kind()+"_id,reason,active) VALUES(?,?,?,?,?)",UUID.randomUUID(),user,r.target(),r.reason(),r.active());
        return preferences(user);
    }
    boolean permitted(Exercise e,Preferences p,Settings c) {
        if(!p.equipment().containsAll(e.equipment()))return false;
        if(c!=null && (!c.equipment().containsAll(e.equipment()) || c.bannedExercises().contains(e.id()) || !Collections.disjoint(e.patterns(),c.bannedPatterns()) || !Collections.disjoint(e.muscles(),c.bannedMuscles()) || !Collections.disjoint(e.equipment(),c.bannedEquipment())))return false;
        for(var r:p.restrictions())if(r.active()&&switch(r.kind()){case "exercise"->e.id().equals(r.target());case "muscle"->e.muscles().contains(r.target());case "pattern"->e.patterns().contains(r.target());default->e.equipment().contains(r.target());})return false;
        return true;
    }
    void validate(Settings c) {
        validIds(c.equipment(),"equipment");validIds(c.bannedEquipment(),"equipment");validIds(c.bannedExercises(),"wod_exercise");validIds(c.bannedPatterns(),"movement_pattern");validIds(c.bannedMuscles(),"muscle_group");
        if(!c.type().equals("AUTO")){
            var template=db.queryForMap("SELECT * FROM wod_template WHERE type_id=? AND stimulus_id=?",c.type(),c.stimulus());
            require(c.count()>=((Number)template.get("min_exercises")).intValue()&&c.count()<=((Number)template.get("max_exercises")).intValue(),"Exercise count is outside this template's range.");
        }
        require(!c.type().equals("EMOM")||c.duration()>=c.count(),"An EMOM needs at least one minute per movement.");
    }
    // Only this member's completed results and nearby planned sessions influence selection.
    Map<String,Double> exposure(UUID user,LocalDate planned) {
        Map<String,Double> scores=new HashMap<>();
        var records=db.queryForList("SELECT h.snapshot_json,u.status,u.completed_on,u.scheduled_on FROM history_exercise h JOIN user_wod u ON h.wod_id=u.wod_id WHERE u.user_id=? AND ((u.status='completed' AND u.completed_on>=? AND u.completed_on<=?) OR (u.status='planned' AND u.scheduled_on>=? AND u.scheduled_on<=?))",user,LocalDate.now().minusDays(28),LocalDate.now(),planned.minusDays(7),planned.plusDays(7));
        for(var row:records){Item i=decode((String)row.get("snapshot_json"),Item.class);boolean done=row.get("status").equals("completed");LocalDate date=((java.sql.Date)row.get(done?"completed_on":"scheduled_on")).toLocalDate();double weight=done?1.0/(1+Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(date,LocalDate.now()))/7.0):.6;
            scores.merge("exercise:"+i.exercise().id(),weight,Double::sum);for(String m:i.exercise().muscles())scores.merge("muscle:"+m,weight,Double::sum);
        }return scores;
    }
    String chooseType(UUID user,Settings config) {
        var recent=db.queryForList("SELECT w.type_id FROM wods w JOIN user_wod u ON u.wod_id=w.id WHERE u.user_id=? AND u.status<>'skipped' ORDER BY w.generated_at DESC LIMIT 4",String.class,user);
        List<String> choices=new ArrayList<>(List.of("AMRAP","EMOM","FOR_TIME","CHIPPER"));if(config.duration()<config.count())choices.remove("EMOM");Collections.shuffle(choices);
        return choices.stream().min(Comparator.comparingLong(t->recent.stream().filter(t::equals).count())).orElseThrow();
    }
    Prescription dose(Exercise e,Settings c) {
        Dose base=e.levels().get(c.level());double factor=.65+.175*c.intensity();
        int amount=Math.max(1,(int)Math.round(base.quantity()*factor));
        if(c.type().equals("CHIPPER"))amount*=3;
        if(c.type().equals("EMOM"))amount=Math.max(1,Math.min(amount,(int)Math.floor(40/e.secondsPerUnit())));
        double weight=Math.round(base.weight()*factor*2)/2.0;
        return new Prescription(e.id(),amount,weight);
    }
    public Draft generate(UUID user,Generate request) {
        Settings input=request.config();validate(input);Settings c=input.type().equals("AUTO")?input.withType(chooseType(user,input)):input;
        validate(c);Preferences p=preferences(user);List<Exercise> all=catalog.exercises();
        List<Exercise> allowed=all.stream().filter(e->permitted(e,p,c)&&e.difficulty()<=Math.min(6,c.level()+1)).toList();
        if(allowed.size()<c.count())throw conflict("Only "+allowed.size()+" movements fit your equipment, level and bans. Reduce the exercise count or adjust your selections. No bans were ignored.");
        Map<String,Exercise> byId=allowed.stream().collect(Collectors.toMap(Exercise::id,e->e));List<Prescription> sets=new ArrayList<>();
        if(request.exercises()!=null){require(request.exercises().size()==c.count(),"The exercise count must match the workout.");Set<String> unique=new HashSet<>();
            for(var set:request.exercises()){require(byId.containsKey(set.exerciseId()),"A selected movement is excluded by your level, equipment or bans.");require(unique.add(set.exerciseId()),"Use each movement only once.");require(Double.isFinite(set.weight()),"Invalid load.");Exercise e=byId.get(set.exerciseId());require(e.category().equals("weightlifting")||set.weight()==0,"External load is only supported on weighted movements.");if(c.type().equals("EMOM"))require(set.quantity()*e.secondsPerUnit()<=45,"This EMOM station exceeds 45 estimated work seconds; lower the quantity to leave rest.");sets.add(set);}
        }else{
            Map<String,Double> recent=exposure(user,c.scheduledOn());Set<String> picked=new HashSet<>(),families=new HashSet<>(),muscles=new HashSet<>(),patterns=new HashSet<>();
            for(int n=0;n<c.count();n++){
                Map<String,Double> noise=new HashMap<>();allowed.forEach(e->noise.put(e.id(),ThreadLocalRandom.current().nextDouble(.8)));
                Exercise best=allowed.stream().filter(e->!picked.contains(e.id())).min(Comparator.comparingDouble(e->{
                    double history=e.muscles().stream().mapToDouble(m->recent.getOrDefault("muscle:"+m,0.0)).average().orElse(0);
                    double repeated=e.muscles().stream().filter(muscles::contains).count()*.3;
                    double stimulus=c.stimulus().equals("engine")?(e.category().equals("conditioning")?-1:0):c.stimulus().equals("strength")?(e.category().equals("weightlifting")?-1:0):0;
                    return history+recent.getOrDefault("exercise:"+e.id(),0.0)*2+(families.contains(e.family())?6:0)+(patterns.containsAll(e.patterns())?1.5:0)+repeated+stimulus+noise.get(e.id());
                })).orElseThrow();sets.add(dose(best,c));picked.add(best.id());families.add(best.family());muscles.addAll(best.muscles());patterns.addAll(best.patterns());
            }
            // Chippers are single-pass workouts, scale their estimated work toward the chosen cap.
            if(c.type().equals("CHIPPER")){
                double estimated=sets.stream().mapToDouble(s->s.quantity()*byId.get(s.exerciseId()).secondsPerUnit()).sum();double scale=Math.max(.5,Math.min(5,c.duration()*60*.75/estimated));
                sets=sets.stream().map(s->new Prescription(s.exerciseId(),Math.max(1,(int)Math.round(s.quantity()*scale)),s.weight())).collect(Collectors.toCollection(ArrayList::new));
            }
        }
        Set<String> selected=sets.stream().map(Prescription::exerciseId).collect(Collectors.toSet());List<Item> items=new ArrayList<>();
        for(var s:sets){Exercise e=byId.get(s.exerciseId());List<Prescription> alternatives=allowed.stream().filter(a->!selected.contains(a.id())&&a.difficulty()<=e.difficulty()&&(a.family().equals(e.family())||!Collections.disjoint(a.patterns(),e.patterns())))
            .sorted(Comparator.comparingInt((Exercise a)->a.family().equals(e.family())?0:1).thenComparingInt(Exercise::difficulty)).limit(5).map(a->dose(a,c)).toList();items.add(new Item(e,s.quantity(),s.weight(),alternatives));}
        int rounds=c.type().equals("CHIPPER")?1:c.type().equals("EMOM")?(int)Math.ceil((double)c.duration()/c.count()):c.type().equals("AMRAP")?0:c.rounds();
        double perRound=items.stream().mapToDouble(i->i.quantity()*i.exercise().secondsPerUnit()).sum();
        double averageSkill=items.stream().mapToInt(i->i.exercise().difficulty()).average().orElse(1);
        double doseRatio=items.stream().mapToDouble(i->{Prescription base=dose(i.exercise(),c);return ((double)i.quantity()/base.quantity()+(base.weight()>0?i.weight()/base.weight():1))/2;}).average().orElse(1);
        double difficulty=Math.round(Math.max(1,Math.min(10,averageSkill+(.6*c.level())+.7*(c.intensity()-3)+Math.max(-1,Math.min(2,doseRatio-1))))*10)/10.0;
        String instructions=switch(c.type()){
            case "AMRAP"->"For "+c.duration()+" minutes, repeat the circuit for as many quality rounds and reps as possible. Rest as needed. Record completed rounds plus extra reps.";
            case "EMOM"->"For "+c.duration()+" minutes, start one station at the beginning of each minute, cycling through the listed order. Rest for the remainder of the minute. The final cycle may be partial.";
            case "CHIPPER"->"Complete each movement in order once. Finish all its prescribed work before moving on. Time cap: "+c.duration()+" minutes.";
            default->"Complete "+rounds+" rounds in order for time. Time cap: "+c.duration()+" minutes. Record your time or unfinished work in notes.";
        };
        List<String> explanations=new ArrayList<>(List.of("All equipment requirements and active bans were checked.","Selection favors less-used muscles and movements from your last 28 days of completed WODs and plans within 7 days of this date.","Difficulty is a programming estimate (1–10), not a medical or performance assessment."));
        Set<String> covered=items.stream().flatMap(i->i.exercise().muscles().stream()).collect(Collectors.toSet());
        explanations.add("Coverage in this WOD: "+String.join(", ",new TreeSet<>(covered))+". Balance is an aim across sessions, not a guarantee for a single WOD.");
        if(c.type().equals("FOR_TIME")&&perRound*rounds>c.duration()*60)explanations.add("Estimated moving time exceeds the cap. Consider fewer rounds or lower quantities; it is fine to stop at the cap.");
        return new Draft(c,items,rounds,difficulty,instructions,explanations);
    }
    void lockMember(UUID id,UUID user,boolean owner) {
        var rows=db.queryForList("SELECT owner_id FROM wods WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()||db.queryForObject("SELECT COUNT(*) FROM user_wod WHERE wod_id=? AND user_id=?",Integer.class,id,user)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"WOD not found.");
        if(owner&&!rows.getFirst().get("owner_id").equals(user))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the organizer can edit or add participants.");
    }
    void snapshots(UUID id,Draft d) {
        db.update("DELETE FROM history_exercise WHERE wod_id=?",id);int position=0;
        for(var item:d.items())db.update("INSERT INTO history_exercise VALUES(?,?,?,?)",id,position++,item.exercise().id(),encode(item));
    }
    @Transactional public Map<String,Object> save(UUID user,Save request,UUID existing) {
        Draft draft=generate(user,new Generate(request.config(),request.exercises()));UUID id=existing==null?UUID.randomUUID():existing;
        if(existing==null){db.update("INSERT INTO wods(id,owner_id,title,type_id,stimulus_id,level_id,duration_minutes,rounds,intensity,assessed_difficulty,instructions,config_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,user,request.title().trim(),draft.config().type(),draft.config().stimulus(),draft.config().level(),draft.config().duration(),draft.rounds(),draft.config().intensity(),draft.difficulty(),draft.instructions(),encode(draft.config()));
            db.update("INSERT INTO user_wod(wod_id,user_id,scheduled_on) VALUES(?,?,?)",id,user,draft.config().scheduledOn());
        }else{
            lockMember(id,user,true);
            require(request.revision()!=null,"A revision is required when editing.");
            if(Boolean.TRUE.equals(db.queryForObject("SELECT frozen FROM wods WHERE id=?",Boolean.class,id)))throw conflict("Completed WODs are immutable. Repeat it as a new WOD to make changes.");
            for(UUID participant:db.queryForList("SELECT user_id FROM user_wod WHERE wod_id=?",UUID.class,id))if(!participant.equals(user))checkParticipant(participant,draft.items());
            int changed=db.update("UPDATE wods SET title=?,type_id=?,stimulus_id=?,level_id=?,duration_minutes=?,rounds=?,intensity=?,assessed_difficulty=?,instructions=?,config_json=?,revision=revision+1 WHERE id=? AND revision=?",request.title().trim(),draft.config().type(),draft.config().stimulus(),draft.config().level(),draft.config().duration(),draft.rounds(),draft.config().intensity(),draft.difficulty(),draft.instructions(),encode(draft.config()),id,request.revision());
            if(changed==0)throw conflict("This WOD changed in another session. Reload before editing.");
            db.update("UPDATE user_wod SET scheduled_on=? WHERE wod_id=? AND user_id=?",draft.config().scheduledOn(),id,user);
        }
        snapshots(id,draft);return detail(user,id);
    }
    public List<Map<String,Object>> history(UUID user) {
        return db.queryForList("SELECT w.id,w.title,w.type_id,w.stimulus_id,w.level_id,w.duration_minutes,w.rounds,w.assessed_difficulty,w.generated_at,w.owner_id,w.revision,u.scheduled_on,u.status,u.completed_on,u.effort FROM wods w JOIN user_wod u ON u.wod_id=w.id WHERE u.user_id=? ORDER BY u.scheduled_on DESC,w.generated_at DESC",user);
    }
    public Map<String,Object> detail(UUID user,UUID id) {
        var rows=db.queryForList("SELECT w.*,u.scheduled_on,u.status,u.completed_on,u.duration_seconds,u.rounds_completed,u.extra_reps,u.effort,u.notes FROM wods w JOIN user_wod u ON u.wod_id=w.id WHERE w.id=? AND u.user_id=?",id,user);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"WOD not found.");
        Map<String,Object> result=new LinkedHashMap<>(rows.getFirst());result.put("config",decode((String)result.remove("config_json"),Settings.class));result.put("items",items(id));result.put("isOwner",result.get("owner_id").equals(user));
        result.put("canEdit",result.get("owner_id").equals(user)&&!Boolean.TRUE.equals(result.get("frozen")));
        // Other participants' personal results and notes are private.
        result.put("participants",db.queryForList("SELECT a.id,a.name FROM athletes a JOIN user_wod u ON u.user_id=a.id WHERE u.wod_id=? ORDER BY u.joined_at,a.id",id));return result;
    }
    List<Item> items(UUID id) {return db.queryForList("SELECT snapshot_json FROM history_exercise WHERE wod_id=? ORDER BY position",String.class,id).stream().map(s->decode(s,Item.class)).toList();}
    @Transactional public Map<String,Object> result(UUID user,UUID id,Result r) {
        lockMember(id,user,false);
        require(!r.status().equals("completed")||r.completedOn()!=null,"Choose a completion date.");
        require(r.status().equals("completed")||r.completedOn()==null,"Only completed WODs can have a completion date.");
        if(r.status().equals("completed"))db.update("UPDATE wods SET frozen=TRUE WHERE id=?",id);
        db.update("UPDATE user_wod SET scheduled_on=?,status=?,completed_on=?,duration_seconds=?,rounds_completed=?,extra_reps=?,effort=?,notes=? WHERE wod_id=? AND user_id=?",r.scheduledOn(),r.status(),r.completedOn(),r.status().equals("completed")?r.durationSeconds():null,r.status().equals("completed")?r.roundsCompleted():null,r.status().equals("completed")?r.extraReps():null,r.status().equals("completed")?r.effort():null,r.notes(),id,user);
        return detail(user,id);
    }
    void checkParticipant(UUID user,List<Item> items) {
        Preferences p=preferences(user);if(items.stream().anyMatch(i->!permitted(i.exercise(),p,null)))throw conflict("This WOD conflicts with a participant's saved equipment or restrictions. Adjust the WOD first.");
    }
    @Transactional public Map<String,Object> addParticipant(UUID user,UUID id,String email) {
        lockMember(id,user,true);
        var matches=db.queryForList("SELECT id FROM athletes WHERE email=?",UUID.class,email.trim().toLowerCase(Locale.ROOT));
        if(matches.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No account matches that email.");UUID other=matches.getFirst();
        if(db.queryForObject("SELECT COUNT(*) FROM user_wod WHERE wod_id=? AND user_id=?",Integer.class,id,other)>0)throw conflict("This user is already a participant.");
        if(db.queryForObject("SELECT COUNT(*) FROM user_wod WHERE wod_id=?",Integer.class,id)>=20)throw conflict("A WOD can have up to 20 participants.");
        checkParticipant(other,items(id));LocalDate date=db.queryForObject("SELECT scheduled_on FROM user_wod WHERE wod_id=? AND user_id=?",LocalDate.class,id,user);
        db.update("INSERT INTO user_wod(wod_id,user_id,scheduled_on) VALUES(?,?,?)",id,other,date);return detail(user,id);
    }
    @Transactional public void leave(UUID user,UUID id) {
        lockMember(id,user,false);UUID owner=db.queryForObject("SELECT owner_id FROM wods WHERE id=?",UUID.class,id);
        db.update("DELETE FROM user_wod WHERE wod_id=? AND user_id=?",id,user);
        var others=db.queryForList("SELECT user_id FROM user_wod WHERE wod_id=? ORDER BY joined_at,user_id",UUID.class,id);
        if(others.isEmpty())db.update("DELETE FROM wods WHERE id=?",id);
        else if(owner.equals(user))db.update("UPDATE wods SET owner_id=?,revision=revision+1 WHERE id=?",others.getFirst(),id);
    }
    public Map<String,Object> statistics(UUID user) {
        LocalDate today=LocalDate.now(),start=today.minusDays(27),monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var completed=db.queryForList("SELECT u.*,w.type_id FROM user_wod u JOIN wods w ON w.id=u.wod_id WHERE u.user_id=? AND u.status='completed' AND u.completed_on>=? AND u.completed_on<=?",user,start,today);
        Map<String,Integer> muscleCounts=new TreeMap<>();ids("muscle_group").forEach(m->muscleCounts.put(m,0));Map<String,Integer> types=new TreeMap<>();ids("wod_type").forEach(t->types.put(t,0));Map<String,Integer> movements=new HashMap<>();
        List<Map<String,Object>> weeks=new ArrayList<>();for(int n=7;n>=0;n--){LocalDate from=monday.minusWeeks(n);int count=db.queryForObject("SELECT COUNT(*) FROM user_wod WHERE user_id=? AND status='completed' AND completed_on>=? AND completed_on<?",Integer.class,user,from,from.plusWeeks(1));weeks.add(Map.of("week",from,"count",count));}
        double effort=0;int effortCount=0,seconds=0;Set<LocalDate> days=new HashSet<>();
        for(var row:completed){UUID id=(UUID)row.get("wod_id");Set<String> hit=new HashSet<>();for(Item i:items(id)){hit.addAll(i.exercise().muscles());movements.merge(i.exercise().id(),1,Integer::sum);}hit.forEach(m->muscleCounts.merge(m,1,Integer::sum));types.merge((String)row.get("type_id"),1,Integer::sum);days.add(((java.sql.Date)row.get("completed_on")).toLocalDate());if(row.get("effort")!=null){effort+=((Number)row.get("effort")).doubleValue();effortCount++;}if(row.get("duration_seconds")!=null)seconds+=((Number)row.get("duration_seconds")).intValue();}
        int max=muscleCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0),min=muscleCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        Map<String,Object> result=new LinkedHashMap<>();result.put("completed",completed.size());result.put("trainingDays",days.size());result.put("loggedMinutes",Math.round(seconds/60.0));result.put("averageEffort",effortCount==0?null:Math.round(effort/effortCount*10)/10.0);result.put("upcoming",db.queryForObject("SELECT COUNT(*) FROM user_wod WHERE user_id=? AND status='planned' AND scheduled_on>=?",Integer.class,user,today));result.put("weekly",weeks);result.put("muscles",muscleCounts);result.put("types",types);result.put("uniqueMovements",movements.size());result.put("mostHit",max==0?List.of():muscleCounts.entrySet().stream().filter(e->e.getValue()==max).map(Map.Entry::getKey).toList());result.put("leastHit",completed.isEmpty()?List.of():muscleCounts.entrySet().stream().filter(e->e.getValue()==min).map(Map.Entry::getKey).toList());result.put("windowStart",start);result.put("windowEnd",today);return result;
    }
}
