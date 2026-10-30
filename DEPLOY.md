# Deploying the backend

The pipeline (`.github/workflows/deploy.yml`) tests the code, builds the JAR, copies it to `/opt/eshop-backend`, restarts it
with pm2 and checks `http://127.0.0.1:8080/api/health`. If the new version does not start, it puts the previous JAR back.

## One-time server setup

1. **New database.** The old database belongs to the previous system, so keep it untouched and use a new one:
   ```bash
   sudo -u postgres psql -c "CREATE USER sales_app WITH PASSWORD 'CHOOSE-A-LONG-PASSWORD';" \
                         -c "CREATE DATABASE sales_bar OWNER sales_app;"
   ```
   Back the old one up first: `sudo -u postgres pg_dump eshop | gzip > ~/eshop-before-bar-$(date +%F).sql.gz`
2. **Settings file** `/opt/eshop-backend/.env` (`chmod 600`, owned by the user the app runs as):
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sales_bar
   SPRING_DATASOURCE_USERNAME=sales_app
   SPRING_DATASOURCE_PASSWORD=...
   APPLICATION_SECURITY_JWT_SECRET_KEY=...        # openssl rand -base64 64
   SPRING_MAIL_USERNAME=...
   SPRING_MAIL_PASSWORD=...
   SPRING_WEB_CORS_ALLOWED_ORIGINS=https://sales.rw,https://www.sales.rw
   APP_FRONTEND_URL=https://sales.rw
   SUPER_ADMIN_EMAIL=you@yourdomain
   SUPER_ADMIN_PASSWORD=...                       # 8+ characters, only used on the very first start
   ```
   Remove the two `SUPER_ADMIN_*` lines after the first start.
3. **pm2.** Replace `/opt/eshop-backend/ecosystem.config.cjs` with `deploy/ecosystem.config.cjs` (it holds no secrets; the app reads
   `.env`). Each deploy deletes and recreates the pm2 process from that file. Run `pm2 startup` once so it survives a reboot.
4. **GitHub secrets** (repository Settings, Environments, `production`): `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY`,
   optional `SERVER_SSH_PORT`. Use a deploy key made only for this: `ssh-keygen -t ed25519 -f deploy_key -N ""`, put
   `deploy_key.pub` in the server user's `~/.ssh/authorized_keys` and the private file in `SERVER_SSH_KEY`.

## Every deploy

Merge or push to `main`. Watch it under the Actions tab. To redeploy without a change, use "Run workflow".

## Rollback

Automatic when the health check fails. By hand: `cp /opt/eshop-backend/releases/previous.jar /opt/eshop-backend/target/StockManagement-0.0.1-SNAPSHOT.jar && pm2 restart eshop-backend`.
