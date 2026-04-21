package dev.folomkin.tasks;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repo;

    @Transactional
    public void processNewUsers(List<User> users, RegionType region) {
        if (users.isEmpty()) return;

        // 1. Проставляем Enum каждому пользователю
        users.forEach(u -> u.setRegion(region));

        // 2. Сохраняем пачкой (Hibernate + Batching)
        repo.saveAll(users);
    }
}