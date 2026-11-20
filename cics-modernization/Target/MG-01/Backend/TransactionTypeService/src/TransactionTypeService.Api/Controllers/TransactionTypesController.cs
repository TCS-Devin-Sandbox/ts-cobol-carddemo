using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TransactionTypeService.Api.Dtos;
using TransactionTypeService.Api.Security;
using TransactionTypeService.Api.Services;

namespace TransactionTypeService.Api.Controllers;

[ApiController]
[Route("api/v1/transaction-types")]
[Produces("application/json")]
public class TransactionTypesController(ITransactionTypeService service) : ControllerBase
{
    private string CurrentUser => User.FindFirstValue("sub") ?? User.Identity?.Name ?? "unknown";

    [HttpGet]
    [Authorize(Policy = AuthorizationPolicies.Read)]
    public Task<TransactionTypePageDto> List([FromQuery] ListQuery query, CancellationToken ct) =>
        service.ListAsync(query, ct);

    [HttpGet("{typeCode}")]
    [Authorize(Policy = AuthorizationPolicies.Read)]
    public Task<TransactionTypeDto> Get(string typeCode, CancellationToken ct) => service.GetAsync(typeCode, ct);

    [HttpPost]
    [Authorize(Policy = AuthorizationPolicies.Write)]
    public async Task<ActionResult<TransactionTypeDto>> Create([FromBody] CreateTransactionTypeRequest request, CancellationToken ct)
    {
        var created = await service.CreateAsync(request, CurrentUser, ct);
        return Created($"/api/v1/transaction-types/{created.TypeCode}", created);
    }

    [HttpPut("{typeCode}")]
    [Authorize(Policy = AuthorizationPolicies.Write)]
    public Task<TransactionTypeDto> Update(string typeCode, [FromBody] UpdateTransactionTypeRequest request, CancellationToken ct) =>
        service.UpdateAsync(typeCode, request, CurrentUser, ct);

    [HttpDelete("{typeCode}")]
    [Authorize(Policy = AuthorizationPolicies.Write)]
    public async Task<IActionResult> Delete(string typeCode, CancellationToken ct)
    {
        await service.DeleteAsync(typeCode, CurrentUser, ct);
        return NoContent();
    }
}
