package com.zosh.campus;

import com.zosh.config.JwtProvider;
import com.zosh.domain.USER_ROLE;
import com.zosh.model.User;
import com.zosh.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/campus/auth")
@RequiredArgsConstructor
public class CampusAuth {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtProvider jwt;

  @PostMapping("/signup")
  @Transactional
  public Map<String, Object> signup(@Valid @RequestBody CampusDtos.Signup input) {
    String email = input.email().trim().toLowerCase(Locale.ROOT);
    if (users.findByEmail(email) != null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    User u = new User();
    u.setEmail(email);
    u.setFullName(input.fullName().trim());
    u.setPassword(passwords.encode(input.password()));
    u.setRole(USER_ROLE.ROLE_CUSTOMER);
    return token(users.saveAndFlush(u));
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody CampusDtos.Login input) {
    User u = users.findByEmail(input.email().trim().toLowerCase(Locale.ROOT));
    if (u == null || !passwords.matches(input.password(), u.getPassword()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    return token(u);
  }

  private Map<String, Object> token(User u) {
    return Map.of(
        "token",
        jwt.generateToken(
            new UsernamePasswordAuthenticationToken(
                u.getEmail(), null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"))),
        "user",
        Map.of("id", u.getId(), "name", u.getFullName()));
  }
}
