package com.apexinstitute.security;

import com.apexinstitute.entity.User;
import com.apexinstitute.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String id) {
        User u = users.findById(Long.parseLong(id))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new org.springframework.security.core.userdetails.User(
                String.valueOf(u.getId()),
                u.getPasswordHash(),
                u.getIsActive(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole()))
        );
    }
}