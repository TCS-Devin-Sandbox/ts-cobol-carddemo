package com.carddemo.menu;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/menus")
public class MenuController {
    public record Option(String name, String program, String userType) { }
    private static final List<Option> MAIN = List.of(
            new Option("Account View", "COACTVWC", "U"),
            new Option("Account Update", "COACTUPC", "U"),
            new Option("Credit Card List", "COCRDLIC", "U"),
            new Option("Credit Card View", "COCRDSLC", "U"),
            new Option("Credit Card Update", "COCRDUPC", "U"),
            new Option("Transaction List", "COTRN00C", "U"),
            new Option("Transaction View", "COTRN01C", "U"),
            new Option("Transaction Add", "COTRN02C", "U"),
            new Option("Transaction Reports", "CORPT00C", "U"),
            new Option("Bill Payment", "COBIL00C", "U"),
            new Option("Pending Authorization View", "COPAUS0C", "U"));
    private static final List<Option> ADMIN = List.of(
            new Option("User List (Security)", "COUSR00C", "A"),
            new Option("User Add (Security)", "COUSR01C", "A"),
            new Option("User Update (Security)", "COUSR02C", "A"),
            new Option("User Delete (Security)", "COUSR03C", "A"),
            new Option("Transaction Type List/Update (Db2)", "COTRTLIC", "A"),
            new Option("Transaction Type Maintenance (Db2)", "COTRTUPC", "A"));

    private List<Option> options(String menu) {
        if (!menu.equals("main") && !menu.equals("admin")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return menu.equals("admin") ? ADMIN : MAIN;
    }

    @GetMapping("/{menu}")
    public Map<String, Object> get(@PathVariable String menu) {
        var options = options(menu);
        return Map.of("tranId", menu.equals("admin") ? "CA00" : "CM00",
                "program", menu.equals("admin") ? "COADM01C" : "COMEN01C",
                "title01", "      AWS Mainframe Modernization       ",
                "title02", "              CardDemo                  ",
                "options", IntStream.range(0, options.size())
                        .mapToObj(i -> String.format("%02d. %-35s", i + 1, options.get(i).name())).toList());
    }

    public record Selection(String option) { }

    @PostMapping("/{menu}/select")
    public ResponseEntity<?> select(@PathVariable String menu, @RequestBody Selection selection,
                                    @AuthenticationPrincipal Jwt jwt) {
        var options = options(menu);
        return select(menu, selection, jwt, options);
    }

    ResponseEntity<?> select(String menu, Selection selection, Jwt jwt, List<Option> options) {
        String value = selection.option() == null ? "" : selection.option().stripTrailing().replace(' ', '0');
        if (!value.matches("[0-9]{1,2}") || Integer.parseInt(value) == 0 || Integer.parseInt(value) > options.size()) {
            return message(400, "Please enter a valid option number...", "error");
        }
        Option option = options.get(Integer.parseInt(value) - 1);
        if (menu.equals("main") && "U".equals(jwt.getClaimAsString("user_type")) && option.userType().equals("A")) {
            return message(403, "No access - Admin Only option... ", "error");
        }
        return target(option);
    }

    ResponseEntity<?> target(Option option) {
        if (option.program().equals("COACTVWC")) {
            return ResponseEntity.ok(Map.of("program", option.program(), "tranId", "CAVW", "route", "/accounts/view"));
        }
        if (option.program().equals("COPAUS0C")) {
            return message(200, "This option " + option.name() + " is not installed...", "error");
        }
        if (option.program().startsWith("DUMMY")) {
            return message(200, "This option " + option.name().split(" ")[0] + "is coming soon ...", "info");
        }
        if (option.program().equals("CORPT00C")) {
            return ResponseEntity.ok(Map.of("program", option.program(), "tranId", "CR00", "route", "/reports"));
        }
        String route = switch (option.program()) {
            case "COUSR00C" -> "/admin/users";
            case "COUSR01C" -> "/admin/users/add";
            case "COUSR02C" -> "/admin/users/update";
            case "COUSR03C" -> "/admin/users/delete";
            case "COTRN00C" -> "/transactions";
            case "COTRN01C" -> "/transactions/view";
            case "COTRTLIC" -> "/transaction-types";
            case "COTRTUPC" -> "/transaction-types/maintain";
            default -> null;
        };
        if (route == null) {
            return message(200, "This option is not installed ...", "info");
        }
        String tranId = switch (option.program()) {
            case "COUSR00C" -> "CU00";
            case "COUSR01C" -> "CU01";
            case "COUSR02C" -> "CU02";
            case "COUSR03C" -> "CU03";
            case "COTRN00C" -> "CT00";
            case "COTRN01C" -> "CT01";
            case "COTRTLIC" -> "CTLI";
            default -> "CTTU";
        };
        return ResponseEntity.ok(Map.of("program", option.program(), "tranId", tranId, "route", route));
    }

    private ResponseEntity<?> message(int status, String text, String severity) {
        return ResponseEntity.status(status).body(Map.of("message", text, "severity", severity));
    }
}
