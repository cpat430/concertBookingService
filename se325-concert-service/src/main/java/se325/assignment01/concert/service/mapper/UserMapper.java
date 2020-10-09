package se325.assignment01.concert.service.mapper;

import se325.assignment01.concert.common.dto.UserDTO;
import se325.assignment01.concert.service.domain.User;

public class UserMapper {

    public static User toDomainModel(UserDTO dtoUser) {

        User fullUser = new User(
                dtoUser.getUsername(),
                dtoUser.getPassword()
        );

        return fullUser;
    }
}
