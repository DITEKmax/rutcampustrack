package ru.rutcampustrack.academic.contract.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessModels.*;

@RequestMapping("/academic")
public interface AdminAccessApi {
    @GetMapping("/admin-access/catalog")
    ResponseEntity<Catalog> catalog();

    @GetMapping("/admin-access/me")
    ResponseEntity<Profile> me();

    @GetMapping("/users/{id}/admin-access")
    ResponseEntity<Profile> get(@PathVariable long id);

    @PutMapping("/users/{id}/admin-access")
    ResponseEntity<Profile> put(@PathVariable long id, @RequestBody Put request);

    @DeleteMapping("/users/{id}/admin-access")
    ResponseEntity<Void> delete(@PathVariable long id, @RequestBody Delete request);
}
