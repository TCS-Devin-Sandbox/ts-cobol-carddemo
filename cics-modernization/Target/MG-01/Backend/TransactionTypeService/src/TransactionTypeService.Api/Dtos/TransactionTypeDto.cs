namespace TransactionTypeService.Api.Dtos;

public record TransactionTypeDto(string TypeCode, string Description);

public record CreateTransactionTypeRequest(string? TypeCode, string? Description);

public record UpdateTransactionTypeRequest(string? Description);

public record TransactionTypePageDto(
    IReadOnlyList<TransactionTypeDto> Items,
    string? FirstCode,
    string? LastCode,
    bool HasNext,
    bool HasPrevious,
    bool Filtered);

public class ListQuery
{
    public string? TypeCode { get; set; }
    public string? Description { get; set; }
    public string? After { get; set; }
    public string? Before { get; set; }
    public int? PageSize { get; set; }
}
