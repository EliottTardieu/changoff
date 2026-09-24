package dev.changoff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static dev.changoff.WodModels.*;

@RestController
@RequestMapping("/api/wods")
public class WodApi {
    private final Api auth;private final WodService service;private final WodCatalog catalog;
    public WodApi(Api auth,WodService service,WodCatalog catalog){this.auth=auth;this.service=service;this.catalog=catalog;}
    @GetMapping("/catalog") public Map<String,Object> catalog(HttpServletRequest r){auth.user(r);return catalog.all();}
    @GetMapping("/preferences") public Preferences preferences(HttpServletRequest r){return service.preferences(auth.user(r));}
    @PutMapping("/preferences") public Preferences preferences(@Valid @RequestBody Preferences p,HttpServletRequest r){return service.savePreferences(auth.user(r),p);}
    @PostMapping("/generate") public Draft generate(@Valid @RequestBody Generate p,HttpServletRequest r){return service.generate(auth.user(r),p);}
    @GetMapping public List<Map<String,Object>> list(HttpServletRequest r){return service.history(auth.user(r));}
    @GetMapping("/statistics") public Map<String,Object> statistics(HttpServletRequest r){return service.statistics(auth.user(r));}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Map<String,Object> save(@Valid @RequestBody Save p,HttpServletRequest r){return service.save(auth.user(r),p,null);}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID id,HttpServletRequest r){return service.detail(auth.user(r),id);}
    @PutMapping("/{id}") public Map<String,Object> edit(@PathVariable UUID id,@Valid @RequestBody Save p,HttpServletRequest r){return service.save(auth.user(r),p,id);}
    @PutMapping("/{id}/result") public Map<String,Object> result(@PathVariable UUID id,@Valid @RequestBody Result p,HttpServletRequest r){return service.result(auth.user(r),id,p);}
    @PostMapping("/{id}/participants") public Map<String,Object> share(@PathVariable UUID id,@Valid @RequestBody Participant p,HttpServletRequest r){return service.addParticipant(auth.user(r),id,p.email());}
    @DeleteMapping("/{id}/participation") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void leave(@PathVariable UUID id,HttpServletRequest r){service.leave(auth.user(r),id);}
}
