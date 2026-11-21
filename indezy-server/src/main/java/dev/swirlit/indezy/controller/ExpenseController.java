package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ExpenseDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A workspace's business expenses and what they change in its net income. Each one answers 404 to another workspace. */
@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Business expenses, by month and category, and their effect by legal status")
public class ExpenseController {

    private final ExpenseService expenses;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's expenses of a year, or all of them, the latest first")
    @GetMapping
    public List<ExpenseDtos.Expense> list(@RequestParam(required = false) Integer year) {
        return expenses.list(workspace(), year);
    }

    @Operation(summary = "A year's totals and what they change in the net income under the workspace's status")
    @GetMapping("/summary/{year}")
    public ExpenseDtos.Summary summary(@PathVariable int year) {
        return expenses.summary(workspace(), year);
    }

    @Operation(summary = "Record an expense")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDtos.Expense create(@Valid @RequestBody ExpenseDtos.Draft draft) {
        return expenses.create(workspace(), draft);
    }

    @Operation(summary = "Change an expense")
    @PutMapping("/{id}")
    public ExpenseDtos.Expense update(@PathVariable Long id, @Valid @RequestBody ExpenseDtos.Draft draft) {
        return expenses.update(workspace(), id, draft);
    }

    @Operation(summary = "Delete an expense")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        expenses.delete(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
