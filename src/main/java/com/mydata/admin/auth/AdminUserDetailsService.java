package com.mydata.admin.auth;

import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public AdminUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        UserEntity user = users.findByEmailAndDeletedAtIsNull(email)
            .filter(candidate -> candidate.getRole() == UserRole.ADMIN)
            .filter(candidate -> candidate.getPasswordHash() != null && !candidate.getPasswordHash().isBlank())
            .orElseThrow(() -> new UsernameNotFoundException("관리자 계정을 찾을 수 없습니다"));

        return new AdminUserDetails(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getPasswordHash(),
            user.getStatus(),
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    public record AdminUserDetails(
        UUID id,
        String email,
        String displayName,
        String password,
        UserStatus status,
        Collection<? extends GrantedAuthority> authorities
    ) implements UserDetails {
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getUsername() {
            return email;
        }

        @Override
        public boolean isEnabled() {
            return status == UserStatus.ACTIVE;
        }
    }
}
