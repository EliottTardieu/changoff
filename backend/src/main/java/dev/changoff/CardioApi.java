package dev.changoff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/cardio")
public class CardioApi {
    public record Benchmark(String activity,int distanceMeters,String source,String reference,List<String> labels,List<Integer> male,List<Integer> female) {}
    public record Input(@NotNull @Pattern(regexp="rope|running|cycling") String activity,
                        @Min(1) @Max(2000000) Integer distanceMeters,
                        @Min(1) @Max(1000000) Integer reps,
                        @NotNull @DecimalMin("0.01") @DecimalMax("604800") Double durationSeconds,
                        @NotNull @PastOrPresent LocalDate performedOn,@Size(max=1000) String notes) {}
    private final Api auth;
    private final JdbcTemplate db;
    private final List<Benchmark> benchmarks;
    public CardioApi(Api auth,JdbcTemplate db,ObjectMapper mapper) throws IOException {
        this.auth=auth;this.db=db;
        try(var stream=getClass().getResourceAsStream("/cardio-benchmarks.json")) {
            benchmarks=mapper.readValue(stream,new TypeReference<>(){});
        }
    }
    @GetMapping("/benchmarks") public List<Benchmark> benchmarks(HttpServletRequest req) {auth.user(req);return benchmarks;}
    Benchmark benchmark(String activity,Integer distance) {
        return benchmarks.stream().filter(b->b.activity().equals(activity)&&Objects.equals(b.distanceMeters(),distance)).findFirst().orElse(null);
    }
    Map<String,Object> ranked(Map<String,Object> row) {
        var result=new LinkedHashMap<>(row);
        String activity=(String)row.get("activity"),standard=(String)row.get("standard");
        Integer distance=row.get("distance_meters")==null?null:((Number)row.get("distance_meters")).intValue();
        double duration=((Number)row.get("duration_seconds")).doubleValue();
        var benchmark=benchmark(activity,distance);
        result.put("rank",null);result.put("source",benchmark==null?null:benchmark.source());
        result.put("reference",benchmark==null?null:benchmark.reference());
        if(benchmark!=null) {
            var times=standard.equals("female")?benchmark.female():benchmark.male();
            String rank="Below beginner benchmark";
            for(int i=0;i<times.size();i++)if(duration<=times.get(i))rank=benchmark.labels().get(i);
            result.put("rank",rank);
        }
        result.put("rate",activity.equals("rope")?((Number)row.get("reps")).doubleValue()*60/duration:distance*3.6/duration);
        return result;
    }
    @GetMapping public List<Map<String,Object>> list(HttpServletRequest req) {
        return db.queryForList("SELECT * FROM cardio_sessions WHERE user_id=? ORDER BY performed_on DESC,created_at DESC",auth.user(req)).stream().map(this::ranked).toList();
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> save(@Valid @RequestBody Input in,HttpServletRequest req) {
        UUID user=auth.user(req);
        if(!Double.isFinite(in.durationSeconds()))throw new IllegalArgumentException("Enter a valid duration.");
        if(in.activity().equals("rope")) {
            if(in.reps()==null||in.distanceMeters()!=null)throw new IllegalArgumentException("Jump rope requires reps and duration, without distance.");
        } else {
            if(in.distanceMeters()==null||in.reps()!=null)throw new IllegalArgumentException("Running and cycling require distance and duration, without reps.");
            if(in.activity().equals("running")&&!Set.of(400,1000,5000,10000,20000).contains(in.distanceMeters()))
                throw new IllegalArgumentException("Choose a supported running distance.");
        }
        UUID id=UUID.randomUUID();String standard=(String)auth.profile(user).get("standard");
        db.update("INSERT INTO cardio_sessions(id,user_id,activity,distance_meters,reps,duration_seconds,standard,performed_on,notes) VALUES(?,?,?,?,?,?,?,?,?)",
            id,user,in.activity(),in.distanceMeters(),in.reps(),in.durationSeconds(),standard,in.performedOn(),in.notes()==null?"":in.notes().trim());
        return ranked(db.queryForMap("SELECT * FROM cardio_sessions WHERE id=? AND user_id=?",id,user));
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,HttpServletRequest req) {
        if(db.update("DELETE FROM cardio_sessions WHERE id=? AND user_id=?",id,auth.user(req))==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cardio entry not found.");
    }
}
