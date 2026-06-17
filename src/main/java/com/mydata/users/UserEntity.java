package com.mydata.users;

import com.mydata.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseEntity {
    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String email;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;

    public static UserEntity create(String email, String displayName) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.displayName = displayName;
        return user;
    }
}
