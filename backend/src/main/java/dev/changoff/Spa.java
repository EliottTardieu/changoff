package dev.changoff;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** SPA entry points; /api is deliberately outside this fallback. */
@Controller
public class Spa {
    @GetMapping({"/", "/en", "/fr", "/en/{*path}", "/fr/{*path}"})
    public String index() {return "forward:/index.html";}
}
