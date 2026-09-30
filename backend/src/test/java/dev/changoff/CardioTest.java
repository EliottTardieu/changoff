package dev.changoff;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cardio;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class CardioTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    Cookie register(String email,String standard) throws Exception {
        var result=mvc.perform(post("/api/auth/register").header("X-Requested-With","changoff").contentType("application/json").content("""
            {"name":"Cardio tester","email":"%s","password":"cardio-password-123","bodyweight":75,"standard":"%s"}
            """.formatted(email,standard))).andExpect(status().isCreated()).andReturn();
        return new Cookie("changoff_session",result.getResponse().getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
    }
    String save(Cookie user,String activity,Integer distance,Integer reps,double seconds,String rank) throws Exception {
        var result=mvc.perform(post("/api/cardio").cookie(user).header("X-Requested-With","changoff").contentType("application/json").content("""
            {"activity":"%s","distanceMeters":%s,"reps":%s,"durationSeconds":%s,"performedOn":"2026-01-01","notes":"My route"}
            """.formatted(activity,distance,reps,seconds))).andExpect(status().isCreated()).andReturn();
        var json=mapper.readTree(result.getResponse().getContentAsString());
        if(rank==null)assertTrue(json.get("rank").isNull());else assertEquals(rank,json.get("rank").asText());
        return json.get("id").asText();
    }
    @Test void cardioHistoryAndRankBoundaries() throws Exception {
        mvc.perform(get("/api/cardio")).andExpect(status().isUnauthorized());
        Cookie a=register("cardio-a@example.com","male"), b=register("cardio-b@example.com","female");
        mvc.perform(get("/api/cardio/benchmarks").cookie(a)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(19));
        String rope=save(a,"rope",null,120,60,null);
        save(a,"running",400,null,99,"Beginner");
        save(a,"running",1000,null,335,"Beginner");
        save(a,"running",5000,null,1351,"Intermediate");
        save(a,"running",5000,null,1351.01,"Novice");
        save(a,"running",10000,null,2198,"Elite");
        save(a,"running",20000,null,10000,"Below beginner benchmark");
        save(a,"cycling",20000,null,2492,"Intermediate");
        save(a,"cycling",21000,null,2400,null); // Never extrapolate from a nearby distance.
        save(b,"running",400,null,80,"Intermediate Recreational");
        save(b,"running",5000,null,1567,"Intermediate");
        mvc.perform(get("/api/cardio").cookie(b)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(delete("/api/cardio/"+rope).cookie(b).header("X-Requested-With","changoff")).andExpect(status().isNotFound());
        mvc.perform(put("/api/me").cookie(a).header("X-Requested-With","changoff").contentType("application/json").content("""
            {"name":"Cardio tester","bodyweight":75,"standard":"female","ranksEnabled":false}
            """)).andExpect(status().isOk());
        mvc.perform(get("/api/cardio").cookie(a)).andExpect(jsonPath("$.length()").value(9)).andExpect(jsonPath("$[0].standard").value("male"));
        mvc.perform(delete("/api/cardio/"+rope).cookie(a).header("X-Requested-With","changoff")).andExpect(status().isNoContent());
        mvc.perform(get("/api/cardio").cookie(a)).andExpect(jsonPath("$.length()").value(8));
        mvc.perform(get("/cardio").cookie(a)).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidMeasurements() throws Exception {
        Cookie user=register("cardio-validation@example.com","male");
        for(String body:new String[]{
            "{\"activity\":\"running\",\"distanceMeters\":2000,\"durationSeconds\":600}",
            "{\"activity\":\"rope\",\"durationSeconds\":60}",
            "{\"activity\":\"rope\",\"reps\":120,\"distanceMeters\":100,\"durationSeconds\":60}",
            "{\"activity\":\"cycling\",\"distanceMeters\":0,\"durationSeconds\":60}",
            "{\"activity\":\"cycling\",\"distanceMeters\":1000,\"durationSeconds\":0}",
            "{\"activity\":\"rope\",\"reps\":-1,\"durationSeconds\":60}"}) {
            var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body);node.put("performedOn","2026-01-01");
            mvc.perform(post("/api/cardio").cookie(user).header("X-Requested-With","changoff").contentType("application/json").content(node.toString())).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/cardio").cookie(user).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
}
