package dev.changoff;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static dev.changoff.WodModels.*;

@Service
public class WodCatalog {
    private final JdbcTemplate db;
    public WodCatalog(JdbcTemplate db) {this.db=db;}
    List<String> links(String table,String column,String id) {return db.queryForList("SELECT "+column+" FROM "+table+" WHERE exercise_id=? ORDER BY "+column,String.class,id);}
    public List<Exercise> exercises() {
        return db.query("SELECT * FROM wod_exercise ORDER BY name",(rs,row)->{
            String id=rs.getString("id");Map<Integer,Dose> doses=new LinkedHashMap<>();
            db.query("SELECT * FROM exercise_level_config WHERE exercise_id=? ORDER BY level_id",(org.springframework.jdbc.core.RowCallbackHandler)r->doses.put(r.getInt("level_id"),new Dose(r.getInt("suggested_reps"),r.getDouble("suggested_weight"))),id);
            return new Exercise(id,rs.getString("name"),rs.getString("family_id"),rs.getString("category_id"),rs.getString("movement_type_id"),rs.getInt("difficulty"),rs.getString("unit"),rs.getDouble("seconds_per_unit"),rs.getString("note"),links("exercise_equipment","equipment_id",id),links("exercise_pattern","pattern_id",id),links("exercise_muscle","muscle_id",id),doses);
        });
    }
    public Map<String,Object> all() {
        Map<String,Object> result=new LinkedHashMap<>();result.put("exercises",exercises());
        for(var entry:Map.of("equipment","equipment","patterns","movement_pattern","muscles","muscle_group","levels","wod_level","types","wod_type","stimuli","wod_stimulus","families","exercise_family","categories","exercise_category","movementTypes","movement_type").entrySet())result.put(entry.getKey(),db.queryForList("SELECT * FROM "+entry.getValue()+" ORDER BY id"));
        result.put("templates",db.queryForList("SELECT * FROM wod_template ORDER BY id"));return result;
    }
}
