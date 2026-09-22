package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

/**
 * Minimal teacher candidate returned by headman subject search.
 *
 * <p>The lookup deliberately excludes login, contact data and role grants.
 * Employee number is included because it is the accepted teacher-picker
 * identifier in the headman workflow.</p>
 */
@Schema(description = "Минимальный профиль преподавателя для выбора в предмете")
public class TeacherLookupResponse extends RepresentationModel<TeacherLookupResponse> {

    private Long id;
    private String fullName;
    private String employeeNumber;

    public TeacherLookupResponse() {}

    public TeacherLookupResponse(Long id, String fullName, String employeeNumber) {
        this.id = id;
        this.fullName = fullName;
        this.employeeNumber = employeeNumber;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }
}
