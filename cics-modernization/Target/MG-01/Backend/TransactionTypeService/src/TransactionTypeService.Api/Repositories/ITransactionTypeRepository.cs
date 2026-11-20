using Microsoft.EntityFrameworkCore.Storage;
using TransactionTypeService.Api.Domain;

namespace TransactionTypeService.Api.Repositories;

public interface ITransactionTypeRepository
{
    Task<List<TransactionType>> GetForwardAsync(string? typeCode, string? descriptionContains, string? after, int take, CancellationToken ct);
    Task<List<TransactionType>> GetBackwardAsync(string? typeCode, string? descriptionContains, string before, int take, CancellationToken ct);
    Task<bool> ExistsBeforeAsync(string? typeCode, string? descriptionContains, string firstCode, CancellationToken ct);
    Task<TransactionType?> FindAsync(string typeCode, CancellationToken ct);
    Task<TransactionType?> FindForUpdateAsync(string typeCode, CancellationToken ct);
    void Add(TransactionType transactionType);
    Task<int> DeleteAsync(string typeCode, CancellationToken ct);
    Task SaveChangesAsync(CancellationToken ct);
    Task<IDbContextTransaction> BeginTransactionAsync(CancellationToken ct);
}
