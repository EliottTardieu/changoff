package dev.changoff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

@Service
public class Ranking {
    public record Exercise(String id, String name, String group, String note, double[] male, double[] female, boolean bodyweightMovement, String source) {}
    public record Target(int level, String rank, double ratio, double weight, int reps) {}
    private final List<Exercise> exercises;
    private static final String[] TIERS = {"Bronze","Silver","Gold","Platinum","Emerald","Diamond","Master","Chang"};
    public Ranking(ObjectMapper mapper) throws IOException {
        try(var in = getClass().getResourceAsStream("/exercises.json")) { exercises = mapper.readValue(in,new TypeReference<>(){}); }
    }
    public List<Exercise> exercises() { return exercises; }
    public Exercise exercise(String id) { return exercises.stream().filter(e->e.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown exercise.")); }
    public static double estimate(double weight,int reps) { return reps==1 ? weight : weight*(1+reps/30.0); }
    public double score(Exercise e,double weight,int reps,double bodyweight) { return estimate(weight+(e.bodyweightMovement()?bodyweight:0),reps)/bodyweight; }
    // Source anchors at Bronze 1, Silver 1, Platinum 1, Diamond 1, Master 3.
    // Chang extends beyond the source elite benchmark; it is an app-defined challenge.
    public double threshold(Exercise e,String standard,int level) {
        double[] a=standard.equals("female")?e.female():e.male();
        int[] positions={1,4,10,16,21,24};
        double[] values={a[0],a[1],a[2],a[3],a[4],a[4]*1.15};
        for(int i=1;i<positions.length;i++) if(level<=positions[i]) return values[i-1]+(values[i]-values[i-1])*(level-positions[i-1])/(positions[i]-positions[i-1]);
        return values[5];
    }
    public int level(Exercise e,String standard,double score) { int level=0; for(int i=1;i<=24;i++) if(score+1e-9>=threshold(e,standard,i))level=i; return level; }
    public static String rank(int level) { return level==0?"Unranked":TIERS[(level-1)/3]+" "+((level-1)%3+1); }
    public List<Target> targets(Exercise e,String standard,double bodyweight,int reps) {
        return java.util.stream.IntStream.rangeClosed(1,24).mapToObj(i->{
            double ratio=threshold(e,standard,i);
            double weight=ratio*bodyweight/(reps==1?1:1+reps/30.0)-(e.bodyweightMovement()?bodyweight:0);
            // Round UP so the displayed set actually reaches the threshold.
            return new Target(i,rank(i),ratio,Math.ceil(Math.max(0,weight)*10-1e-9)/10,reps);
        }).toList();
    }
}
