package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.UserDTO;
import com.mainuddin.codereview.entity.User;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public UserDTO getUserByGithubId(String githubId) {
        User user = userRepository.findByGithubId(githubId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with GitHub ID: " + githubId));
        return convertToDTO(user);
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .githubId(user.getGithubId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .build();
    }
}
