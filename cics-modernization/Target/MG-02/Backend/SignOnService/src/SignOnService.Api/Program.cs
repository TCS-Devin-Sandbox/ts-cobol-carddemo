using System.Security.Authentication;
using System.Text.Json.Serialization;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Authorization.Policy;
using Microsoft.EntityFrameworkCore;
using SignOnService.Api.Audit;
using SignOnService.Api.Auth;
using SignOnService.Api.Data;
using SignOnService.Api.Errors;
using SignOnService.Api.Menus;
using SignOnService.Api.Security;

var builder = WebApplication.CreateBuilder(args);
var isDevelopment = builder.Environment.IsDevelopment();
StartupGuards.Validate(builder.Configuration, builder.Environment);

var connectionString = builder.Configuration.GetConnectionString("SignOn")
                       ?? throw new InvalidOperationException("Connection string 'SignOn' is not configured (set ConnectionStrings__SignOn).");
var authOptions = builder.Configuration.GetSection("Auth").Get<AuthOptions>() ?? new AuthOptions();
var menuOptions = new MenuRouteOptions();
builder.Configuration.GetSection("Menu").Bind(menuOptions);
var signingKeys = new SigningKeys(authOptions);

builder.WebHost.ConfigureKestrel(options =>
{
    options.Limits.MaxRequestBodySize = 1024;
    options.AddServerHeader = false;
    if (!isDevelopment)
    {
        options.ConfigureHttpsDefaults(https => https.SslProtocols = SslProtocols.Tls13);
    }
});

builder.Services.AddDbContext<SignOnDbContext>(o => o.UseNpgsql(connectionString));
builder.Services.AddScoped<ISecurityUserStore, SecurityUserStore>();
builder.Services.AddSingleton(authOptions);
builder.Services.AddSingleton(menuOptions);
builder.Services.AddSingleton(signingKeys);
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.AddSingleton<TokenIssuer>();
builder.Services.AddScoped<SignInService>();
builder.Services.AddSingleton<MenuService>();
builder.Services.AddHttpContextAccessor();
builder.Services.AddSingleton<IAuditLog, AuditLog>();
builder.Services.AddOpenApi();
builder.Services.Configure<RouteHandlerOptions>(o => o.ThrowOnBadRequest = true);
builder.Services.ConfigureHttpJsonOptions(o => o.SerializerOptions.UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow);

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(o =>
    {
        o.MapInboundClaims = false;
        o.TokenValidationParameters.ValidIssuer = authOptions.Issuer;
        o.TokenValidationParameters.ValidAudience = authOptions.Audience;
        o.TokenValidationParameters.IssuerSigningKey = signingKeys.Key;
        o.TokenValidationParameters.NameClaimType = "sub";
        o.TokenValidationParameters.RoleClaimType = "role";
        o.TokenValidationParameters.ClockSkew = TimeSpan.FromMinutes(2);
    });
builder.Services.AddAuthorization(o =>
{
    o.AddPolicy(Roles.User, p => p.RequireAuthenticatedUser().RequireRole(Roles.User));
    o.AddPolicy(Roles.Admin, p => p.RequireAuthenticatedUser().RequireRole(Roles.Admin));
});
builder.Services.AddSingleton<IAuthorizationMiddlewareResultHandler, ProblemAuthorizationResultHandler>();

var signInPerMinute = builder.Configuration.GetValue("RateLimit:SignInPerMinute", 5);
builder.Services.AddRateLimiter(o =>
{
    o.OnRejected = (ctx, _) => new ValueTask(ProblemDetailsMiddleware.WriteAsync(ctx.HttpContext, 429,
        ErrorCodes.TooManyRequests, "Too many sign-in attempts. Wait a minute and try again."));
    o.AddPolicy("sign-in", context => RateLimitPartition.GetFixedWindowLimiter(
        context.Connection.RemoteIpAddress?.ToString() ?? "anonymous",
        _ => new FixedWindowRateLimiterOptions { PermitLimit = signInPerMinute, Window = TimeSpan.FromMinutes(1) }));
});

builder.Services.AddCors(o => o.AddPolicy("dev-ui", p => p
    .WithOrigins(builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? [])
    .AllowAnyHeader()
    .WithMethods("GET", "POST")));

var app = builder.Build();

app.UseMiddleware<ProblemDetailsMiddleware>();
app.Use(async (context, next) =>
{
    var headers = context.Response.Headers;
    headers["X-Content-Type-Options"] = "nosniff";
    headers["X-Frame-Options"] = "DENY";
    headers["Referrer-Policy"] = "no-referrer";
    headers["Cache-Control"] = "no-store";
    headers["Content-Security-Policy"] = "default-src 'none'; frame-ancestors 'none'";
    await next();
});
if (!isDevelopment)
{
    app.UseHsts();
    app.UseHttpsRedirection();
}
app.UseCors("dev-ui");
app.UseAuthentication();
app.UseRateLimiter();
app.UseAuthorization();

app.MapPost("/api/v1/auth/sign-in", async (SignInRequest request, SignInService service, CancellationToken ct) =>
        Results.Ok(await service.SignInAsync(request, ct)))
    .AllowAnonymous()
    .RequireRateLimiting("sign-in");
app.MapGet("/api/v1/menus/main", (MenuService menus) => menus.Main()).RequireAuthorization(Roles.User);
app.MapGet("/api/v1/menus/admin", (MenuService menus) => menus.Admin()).RequireAuthorization(Roles.Admin);
app.MapGet("/.well-known/openid-configuration", () => Results.Ok(new
{
    issuer = authOptions.Issuer,
    jwks_uri = $"{authOptions.Issuer.TrimEnd('/')}/.well-known/jwks.json",
    response_types_supported = new[] { "token" },
    subject_types_supported = new[] { "public" },
    id_token_signing_alg_values_supported = new[] { "RS256" }
})).AllowAnonymous();
app.MapGet("/.well-known/jwks.json", (SigningKeys keys) => Results.Ok(keys.Jwks())).AllowAnonymous();
if (isDevelopment)
{
    app.MapOpenApi().AllowAnonymous();
}
app.MapGet("/health", async (SignOnDbContext db, CancellationToken ct) =>
{
    var healthy = await db.Database.CanConnectAsync(ct);
    return healthy
        ? Results.Ok(new { status = "Healthy" })
        : Results.Json(new { status = "Unhealthy" }, statusCode: StatusCodes.Status503ServiceUnavailable);
}).AllowAnonymous();

app.Run();

/// <summary>Returns 401/403 as ProblemDetails with machine codes.</summary>
internal sealed class ProblemAuthorizationResultHandler : IAuthorizationMiddlewareResultHandler
{
    private readonly AuthorizationMiddlewareResultHandler _default = new();

    public Task HandleAsync(RequestDelegate next, HttpContext context, AuthorizationPolicy policy, PolicyAuthorizationResult result)
    {
        if (result.Challenged)
        {
            context.Response.Headers.WWWAuthenticate = "Bearer";
            return ProblemDetailsMiddleware.WriteAsync(context, 401, ErrorCodes.Unauthenticated, "Sign in to continue.");
        }
        if (result.Forbidden)
        {
            return ProblemDetailsMiddleware.WriteAsync(context, 403, ErrorCodes.Forbidden, "This menu is not available for your role.");
        }
        return _default.HandleAsync(next, context, policy, result);
    }
}

public partial class Program;
