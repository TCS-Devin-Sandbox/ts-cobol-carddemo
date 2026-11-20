using Microsoft.EntityFrameworkCore;
using Npgsql;
using TransactionTypeService.Api.Domain;
using TransactionTypeService.Api.Dtos;
using TransactionTypeService.Api.Errors;
using TransactionTypeService.Api.Repositories;
using TransactionTypeService.Api.Validation;

namespace TransactionTypeService.Api.Services;

/// <summary>Business rules of legacy COTRTLIC (list/filter/inline update/delete) and COTRTUPC (lookup/add/update/delete).</summary>
public class TransactionTypeService(ITransactionTypeRepository repository, ILogger<TransactionTypeService> logger)
    : ITransactionTypeService
{
    public async Task<TransactionTypePageDto> ListAsync(ListQuery query, CancellationToken ct)
    {
        var criteria = TransactionTypeValidators.ValidateList(query);
        List<TransactionType> rows;
        bool hasNext;

        if (criteria.Before is not null)
        {
            rows = await repository.GetBackwardAsync(criteria.TypeCode, criteria.DescriptionContains, criteria.Before, criteria.PageSize, ct);
            hasNext = true;
            if (rows.Count == 0)
            {
                (rows, hasNext) = await ForwardPageAsync(criteria, after: null, ct);
            }
        }
        else
        {
            (rows, hasNext) = await ForwardPageAsync(criteria, criteria.After, ct);
        }

        var hasPrevious = rows.Count > 0 &&
                          await repository.ExistsBeforeAsync(criteria.TypeCode, criteria.DescriptionContains, rows[0].TypeCode, ct);

        return new TransactionTypePageDto(
            rows.Select(ToDto).ToList(),
            rows.FirstOrDefault()?.TypeCode,
            rows.LastOrDefault()?.TypeCode,
            hasNext,
            hasPrevious,
            criteria.Filtered);
    }

    public async Task<TransactionTypeDto> GetAsync(string typeCode, CancellationToken ct)
    {
        var code = TransactionTypeValidators.NormalizeCodeOrThrow(typeCode);
        var entity = await repository.FindAsync(code, ct)
                     ?? throw new ApiException(StatusCodes.Status404NotFound, ErrorCodes.TypeNotFound,
                         $"No transaction type with code {code}.");
        return ToDto(entity);
    }

    public async Task<TransactionTypeDto> CreateAsync(CreateTransactionTypeRequest request, string user, CancellationToken ct)
    {
        var (code, description) = TransactionTypeValidators.ValidateCreate(request);
        repository.Add(new TransactionType { TypeCode = code, Description = description });
        try
        {
            await repository.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex) when (ex.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation })
        {
            throw new ApiException(StatusCodes.Status409Conflict, ErrorCodes.TypeAlreadyExists,
                $"Transaction type {code} already exists.",
                new Dictionary<string, string[]> { ["typeCode"] = [$"Transaction type {code} already exists."] });
        }
        logger.LogInformation("Audit: user {User} created transaction type {Code}", user, code);
        return new TransactionTypeDto(code, description);
    }

    public async Task<TransactionTypeDto> UpdateAsync(string typeCode, UpdateTransactionTypeRequest request, string user, CancellationToken ct)
    {
        var code = TransactionTypeValidators.NormalizeCodeOrThrow(typeCode);
        var description = TransactionTypeValidators.ValidateUpdate(request);

        await using var transaction = await repository.BeginTransactionAsync(ct);
        var entity = await repository.FindForUpdateAsync(code, ct)
                     ?? throw NotFoundForChange(code);

        if (string.Equals(entity.Description.Trim(), description, StringComparison.OrdinalIgnoreCase))
        {
            throw new ApiException(StatusCodes.Status422UnprocessableEntity, ErrorCodes.NoChanges,
                "No change detected with respect to database values.");
        }

        entity.Description = description;
        await repository.SaveChangesAsync(ct);
        await transaction.CommitAsync(ct);
        logger.LogInformation("Audit: user {User} updated transaction type {Code}", user, code);
        return ToDto(entity);
    }

    public async Task DeleteAsync(string typeCode, string user, CancellationToken ct)
    {
        var code = TransactionTypeValidators.NormalizeCodeOrThrow(typeCode);
        var deleted = await repository.DeleteAsync(code, ct);
        if (deleted == 0)
        {
            throw NotFoundForChange(code);
        }
        logger.LogInformation("Audit: user {User} deleted transaction type {Code}", user, code);
    }

    private async Task<(List<TransactionType> Rows, bool HasNext)> ForwardPageAsync(
        TransactionTypeValidators.ListCriteria criteria, string? after, CancellationToken ct)
    {
        var rows = await repository.GetForwardAsync(criteria.TypeCode, criteria.DescriptionContains, after, criteria.PageSize + 1, ct);
        var hasNext = rows.Count > criteria.PageSize;
        if (hasNext)
        {
            rows.RemoveAt(rows.Count - 1);
        }
        return (rows, hasNext);
    }

    private static ApiException NotFoundForChange(string code) =>
        new(StatusCodes.Status404NotFound, ErrorCodes.TypeNotFound,
            $"Transaction type {code} was not found. It may have been deleted by someone else.");

    private static TransactionTypeDto ToDto(TransactionType t) => new(t.TypeCode, t.Description);
}
