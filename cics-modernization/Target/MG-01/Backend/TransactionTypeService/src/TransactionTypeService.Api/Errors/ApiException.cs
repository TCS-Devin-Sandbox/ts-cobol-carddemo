namespace TransactionTypeService.Api.Errors;

public class ApiException(int status, string code, string message, IDictionary<string, string[]>? errors = null)
    : Exception(message)
{
    public int Status { get; } = status;
    public string Code { get; } = code;
    public IDictionary<string, string[]>? Errors { get; } = errors;

    public static ApiException Validation(IDictionary<string, string[]> errors) =>
        new(StatusCodes.Status400BadRequest, ErrorCodes.ValidationFailed, "One or more fields are invalid.", errors);
}
