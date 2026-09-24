package dev.changoff;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import java.io.IOException;
import java.util.Map;

@Component
class RequestGuard extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Cache-Control","no-store");
        // Custom non-simple header plus no CORS support prevents cross-origin browser mutations.
        if(!java.util.Set.of("GET","HEAD","OPTIONS").contains(req.getMethod()) && !"changoff".equals(req.getHeader("X-Requested-With"))) {
            res.sendError(403,"Missing request protection header");return;
        }
        chain.doFilter(req,res);
    }
}
@RestControllerAdvice
class Errors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e) {return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request failed":e.getReason()));}
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<?> invalid(Exception e) {
        String message=e instanceof IllegalArgumentException?e.getMessage():"Check your input values and try again.";
        return ResponseEntity.badRequest().body(Map.of("message",message==null?"Invalid input":message));
    }
}
