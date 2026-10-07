package ru.rutcampustrack.academic.contract.api;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.academic.contract.dto.group.GroupOptionsResponse;
@RequestMapping("/academic/groups")
public interface GroupOptionsApi {
    @GetMapping("/options") GroupOptionsResponse options(@RequestParam(defaultValue="") String search,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size);
}
