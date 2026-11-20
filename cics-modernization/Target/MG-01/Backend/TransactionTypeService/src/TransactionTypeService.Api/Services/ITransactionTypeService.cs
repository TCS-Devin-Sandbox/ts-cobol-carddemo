using TransactionTypeService.Api.Dtos;

namespace TransactionTypeService.Api.Services;

public interface ITransactionTypeService
{
    Task<TransactionTypePageDto> ListAsync(ListQuery query, CancellationToken ct);
    Task<TransactionTypeDto> GetAsync(string typeCode, CancellationToken ct);
    Task<TransactionTypeDto> CreateAsync(CreateTransactionTypeRequest request, string user, CancellationToken ct);
    Task<TransactionTypeDto> UpdateAsync(string typeCode, UpdateTransactionTypeRequest request, string user, CancellationToken ct);
    Task DeleteAsync(string typeCode, string user, CancellationToken ct);
}
