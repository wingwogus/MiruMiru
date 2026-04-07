# MiruMiru Runtime Configuration Examples

These examples match the environment variables required by `application-prod.yml`.

## Resources

- `mirumiru-api-secret.example.yaml`
  - Sensitive values for database, JWT signing, and mail credentials
- `mirumiru-api-config.example.yaml`
  - Non-sensitive Redis connection settings

## Usage

1. Copy the example files and replace placeholder values.
2. Apply them into the same namespace as the Helm release.
3. Reference them from `values.yaml`:

```yaml
envFrom:
  - secretRef:
      name: mirumiru-api-secret
  - configMapRef:
      name: mirumiru-api-config
```

## Required Values

### Secret

- `PROD_DB_URL`
- `PROD_DB_USERNAME`
- `PROD_DB_PASSWORD`
- `PROD_JWT_SECRET`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`

### ConfigMap

- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_DB`

## Notes

- `PROD_JWT_SECRET` must be a Base64 string that decodes to at least 64 bytes for HS512.
- `SPRING_PROFILES_ACTIVE=prod` is already set by the chart and does not need to be added here.
