package com.example.farm.config;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.farm.common.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // 3. 把卡塞进验卡机 (现在它会抛出异常了)
                DecodedJWT jwt = JwtUtils.verifyToken(token);

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long userId = jwt.getClaim("userId").asLong();
                    String username = jwt.getClaim("username").asString();
                    String role = "ROLE_" + jwt.getClaim("role").asString();
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId, username, Collections.singletonList(new SimpleGrantedAuthority(role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

            } catch (TokenExpiredException e) {
                // 捕获过期异常，直接打回前端！
                logger.warn("门禁拦截：用户的 Token 已过期");
                renderJson(response, 401, "登录已过期，请重新登录");
                return;

            } catch (JWTVerificationException e) {
                // 捕获其他篡改/伪造异常
                logger.warn("门禁拦截：发现无效或伪造的 Token");
                renderJson(response, 401, "无效的访问凭证，请重新登录");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 辅助方法：使用统一的 Result 实体类向前端输出标准 JSON
     */
    private void renderJson(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        com.example.farm.common.api.Result<Object> result = com.example.farm.common.api.Result.failed(message);
        String json = objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }
}