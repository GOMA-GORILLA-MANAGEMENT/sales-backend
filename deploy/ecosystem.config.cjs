// pm2 process for the API. Secrets are NOT here: the app reads /opt/eshop-backend/.env on start.
module.exports = {
  apps: [
    {
      name: "eshop-backend",
      cwd: "/opt/eshop-backend",
      script: "java",
      args: "-Xms256m -Xmx768m -jar target/StockManagement-0.0.1-SNAPSHOT.jar",
      autorestart: true,
      max_restarts: 10,
      restart_delay: 5000,
    },
  ],
};
