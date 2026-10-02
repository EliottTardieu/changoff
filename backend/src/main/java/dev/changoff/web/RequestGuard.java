package dev.changoff.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestGuard extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest req,
        HttpServletResponse res,
        FilterChain chain
    ) throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Cache-Control", "no-store");
        // Custom non-simple header plus no CORS support prevents cross-origin browser mutations.
        if (
            !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod()) &&
            !"changoff".equals(req.getHeader("X-Requested-With"))
        ) {
            res.sendError(403, "Missing request protection header");
            return;
        }
        chain.doFilter(req, res);
    }
}
