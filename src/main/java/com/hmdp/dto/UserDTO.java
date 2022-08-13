package com.hmdp.dto;

import com.hmdp.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;

    /**
     * user与userDto的映射
     * @param user
     * @return
     */
    public static UserDTO user2UserDto(User user){
        return new UserDTO(user.getId(),  user.getNickName(), user.getIcon());
    }
}
