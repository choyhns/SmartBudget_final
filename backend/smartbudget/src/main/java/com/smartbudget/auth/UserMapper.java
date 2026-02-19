package com.smartbudget.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    UserDTO selectUserById(@Param("userId") Long userId);
    UserDTO selectUserByEmail(@Param("email") String email);
    int insertUser(UserDTO user);
    int updateUser(UserDTO user);
    int deleteUser(@Param("userId") Long userId);
    boolean existsByEmail(@Param("email") String email);

    UserDTO selectUserByProviderAndProviderId(
        @Param("provider") String provider,
        @Param("providerId") String providerId
    );

    int updateProfileByUserId(UserDTO user);
    int updateRequiredProfileByUserId(UserDTO user);
    
}
