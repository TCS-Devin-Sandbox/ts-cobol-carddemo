package com.carddemo.useradmin;

import com.carddemo.useradmin.UserAdminService.Edits;
import com.carddemo.useradmin.UserAdminService.User;
import com.carddemo.useradmin.UserAdminService.UserPage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    public UserPage list(@RequestParam(defaultValue = "") String startId,
                         @RequestParam(defaultValue = "") String after,
                         @RequestParam(defaultValue = "") String before) {
        return users.list(startId, after, before);
    }

    @GetMapping("/record")
    public User fetch(@RequestParam String userId) {
        return users.fetch(userId);
    }

    public record NewUser(String userId, String firstName, String lastName, String password, String userType) { }
    public record Message(String message) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Message add(@RequestBody NewUser user) {
        return new Message(users.add(user.userId(),
                new Edits(user.firstName(), user.lastName(), user.password(), user.userType())));
    }

    @PutMapping("/record")
    public Message update(@RequestParam String userId, @RequestBody Edits edits) {
        return new Message(users.update(userId, edits));
    }

    @DeleteMapping("/record")
    public Message delete(@RequestParam String userId) {
        return new Message(users.delete(userId));
    }
}
