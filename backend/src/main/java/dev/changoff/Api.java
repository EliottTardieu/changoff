package dev.changoff;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class Api {
    final JdbcTemplate db; final Ranking ranking; final boolean secure;
    final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    final String dummyHash=passwords.encode("not-a-real-password");
    public Api(JdbcTemplate db,Ranking ranking,@Value("${app.secure-cookie}") boolean secure) {this.db=db;this.ranking=ranking;this.secure=secure;}
    public record Registration(@NotBlank @Size(max=60) String name,@Email @NotBlank @Size(max=254) String email,@NotNull @Size(min=10,max=72) String password,@NotNull @DecimalMin("30") @DecimalMax("300") Double bodyweight,@Pattern(regexp="male|female") @NotNull String standard) {}
    public record Login(@NotBlank @Email String email,@NotNull @Size(max=72) String password) {}
    public record LiftInput(@NotBlank String exercise,@NotNull @DecimalMin("0") @DecimalMax("1500") Double weight,@Min(1) @Max(12) int reps,@NotNull @PastOrPresent LocalDate performedOn) {}
    public record Profile(@NotBlank @Size(max=60) String name,@NotNull @DecimalMin("30") @DecimalMax("300") Double bodyweight,@NotNull @Pattern(regexp="male|female") String standard) {}
    String email(String e) {return e.trim().toLowerCase(Locale.ROOT);}
    static String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    String token(HttpServletRequest request) {if(request.getCookies()!=null)for(var c:request.getCookies())if(c.getName().equals("changoff_session"))return c.getValue();return "";}
    UUID user(HttpServletRequest request) {
        var ids=db.queryForList("SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP",UUID.class,hash(token(request)));
        if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in.");return ids.getFirst();
    }
    void session(UUID id,HttpServletRequest req,HttpServletResponse res) {
        db.update("DELETE FROM sessions WHERE expires_at<CURRENT_TIMESTAMP OR token_hash=?",hash(token(req)));
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("INSERT INTO sessions VALUES (?,?,?)",hash(token),id,java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(7))));
        cookie(res,token,Duration.ofDays(7));
    }
    void cookie(HttpServletResponse res,String token,Duration age) {res.addHeader("Set-Cookie",ResponseCookie.from("changoff_session",token).httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(age).build().toString());}
    Map<String,Object> profile(UUID id) {return db.queryForMap("SELECT id,name,email,bodyweight,standard FROM athletes WHERE id=?",id);}
    @GetMapping("/health") public Map<String,String> health() {db.queryForObject("SELECT 1",Integer.class);return Map.of("status","UP");}
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> register(@Valid @RequestBody Registration in,HttpServletRequest req,HttpServletResponse res) {
        if(in.password().getBytes(StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes.");
        UUID id=UUID.randomUUID();
        try {db.update("INSERT INTO athletes(id,name,email,password_hash,bodyweight,standard) VALUES (?,?,?,?,?,?)",id,in.name().trim(),email(in.email()),passwords.encode(in.password()),in.bodyweight(),in.standard());}
        catch(DuplicateKeyException e) {throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists.");}
        session(id,req,res);return profile(id);
    }
    @PostMapping("/auth/login") public Map<String,Object> login(@Valid @RequestBody Login in,HttpServletRequest req,HttpServletResponse res) {
        var rows=db.queryForList("SELECT id,password_hash FROM athletes WHERE email=?",email(in.email()));
        String encoded=rows.isEmpty()?dummyHash:(String)rows.getFirst().get("password_hash");
        boolean matches=passwords.matches(in.password(),encoded);
        if(rows.isEmpty()||!matches)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        UUID id=(UUID)rows.getFirst().get("id");session(id,req,res);return profile(id);
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(HttpServletRequest req,HttpServletResponse res) {db.update("DELETE FROM sessions WHERE token_hash=?",hash(token(req)));cookie(res,"",Duration.ZERO);}
    @GetMapping("/me") public Map<String,Object> me(HttpServletRequest req) {return profile(user(req));}
    @PutMapping("/me") public Map<String,Object> update(@Valid @RequestBody Profile in,HttpServletRequest req) {UUID id=user(req);db.update("UPDATE athletes SET name=?,bodyweight=?,standard=? WHERE id=?",in.name().trim(),in.bodyweight(),in.standard(),id);return profile(id);}
    @GetMapping("/exercises") public List<Ranking.Exercise> exercises(HttpServletRequest req) {user(req);return ranking.exercises();}
    @GetMapping("/exercises/{id}/targets") public List<Ranking.Target> targets(@PathVariable String id,@RequestParam(defaultValue="5") int reps,HttpServletRequest req) {
        if(reps<1||reps>12)throw new IllegalArgumentException("Reps must be between 1 and 12.");var p=profile(user(req));return ranking.targets(ranking.exercise(id),(String)p.get("standard"),((Number)p.get("bodyweight")).doubleValue(),reps);
    }
    @GetMapping("/lifts") public List<Map<String,Object>> lifts(HttpServletRequest req) {return db.queryForList("SELECT id,exercise,weight,reps,bodyweight,standard,score,performed_on FROM lifts WHERE user_id=? ORDER BY performed_on DESC,created_at DESC",user(req));}
    @PostMapping("/lifts") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> log(@Valid @RequestBody LiftInput in,HttpServletRequest req) {
        UUID uid=user(req);var p=profile(uid);var e=ranking.exercise(in.exercise());
        if(!Double.isFinite(in.weight())||(!e.bodyweightMovement()&&in.weight()<=0))throw new IllegalArgumentException("Enter a positive weight; bodyweight movements may use 0.");
        double bw=((Number)p.get("bodyweight")).doubleValue();String standard=(String)p.get("standard");double score=ranking.score(e,in.weight(),in.reps(),bw);UUID id=UUID.randomUUID();
        db.update("INSERT INTO lifts(id,user_id,exercise,weight,reps,bodyweight,standard,score,performed_on) VALUES(?,?,?,?,?,?,?,?,?)",id,uid,e.id(),in.weight(),in.reps(),bw,standard,score,in.performedOn());
        return Map.of("id",id,"rank",Ranking.rank(ranking.level(e,standard,score)));
    }
    @DeleteMapping("/lifts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,HttpServletRequest req) {
        if(db.update("DELETE FROM lifts WHERE id=? AND user_id=?",id,user(req))==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Set not found.");
    }
    @GetMapping("/dashboard") public List<Map<String,Object>> dashboard(HttpServletRequest req) {
        UUID id=user(req);var p=profile(id);String standard=(String)p.get("standard");
        return ranking.exercises().stream().map(e->{
            Double best=db.queryForObject("SELECT MAX(score) FROM lifts WHERE user_id=? AND exercise=? AND standard=?",Double.class,id,e.id(),standard);
            double score=best==null?0:best;int level=ranking.level(e,standard,score);
            double low=level==0?0:ranking.threshold(e,standard,level);double high=level==24?low:ranking.threshold(e,standard,level+1);
            Map<String,Object> row=new LinkedHashMap<>();row.put("exercise",e);row.put("level",level);row.put("rank",Ranking.rank(level));row.put("score",score);row.put("progress",level==24?100:Math.max(0,Math.min(100,100*(score-low)/(high-low))));row.put("nextRank",level==24?"Maximum rank":Ranking.rank(level+1));return row;
        }).toList();
    }
}
