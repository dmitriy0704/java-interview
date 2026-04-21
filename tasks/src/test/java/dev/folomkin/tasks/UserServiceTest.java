package dev.folomkin.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldSetRegionAndSaveAllUsers() {
        // 1. Arrange (Подготовка данных)
        User user1 = new User();
        user1.setName("Alice");

        User user2 = new User();
        user2.setName("John");

        List<User> users = List.of(user1, user2);
        RegionType targetRegion = RegionType.CENTRAL;

        // 2. Act (Выполнение действия)
        userService.processNewUsers(users, targetRegion);


        // 3. Assert (Проверка результатов)

        // Используем перехватчик аргументов, чтобы вытащить список, ушедший в saveAll
        ArgumentCaptor<List<User>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository, times(1)).saveAll(captor.capture());

        List<User> savedUsers = captor.getValue();

        // Проверяем количество
        assertEquals(2, savedUsers.size());

        // Проверяем, что каждому проставился нужный регион
        assertEquals(RegionType.CENTRAL, savedUsers.get(0).getRegion());
        assertEquals(RegionType.CENTRAL, savedUsers.get(1).getRegion());

        // Проверяем, что имена не потерялись
        assertEquals("Alice", savedUsers.get(0).getName());
    }

    @Test
    void shouldDoNothingWhenListIsEmpty() {
        // Проверка граничного случая: пустой список не должен дергать репозиторий
        userService.processNewUsers(List.of(), RegionType.WEST);

        verify(userRepository, never()).saveAll(any());
    }
}