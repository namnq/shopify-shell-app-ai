# Shopify Embedded App with AI Capabilities

## 🚀 Tech Stack
- **Backend**: Java 21 + Spring Boot 3
- **Frontend**: React + Shopify App Bridge
- **Build**: Maven + npm
- **Authentication**: Shopify OAuth 2.0

## 📂 Project Structure
```
shopify-app/
├── frontend/      # React frontend (Polaris UI)
├── src/           # Spring Boot backend
│   ├── main/java/com/shopify/
│   └── resources/
├── target/        # Build outputs
└── pom.xml        # Maven config
```

## 🔧 Setup Guide

### Backend
```bash
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm start
```

## 🔐 OAuth Flow
1. Frontend redirects to `/auth/install?shop=your-store.myshopify.com`
2. Backend handles Shopify OAuth handshake
3. Merchant approves permissions
4. Shopify redirects with access token

## 🌐 Deployment Options

### Option 1: EC2 Single-Server Deployment
```bash
# 1. Connect to EC2 and install dependencies
ssh -i your-key.pem ubuntu@your-ec2-ip
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-21-jdk maven npm nginx

# 2. Clone and build backend
git clone https://github.com/namnq/shopify-shell-app-ai.git
cd shopify-shell-app-ai
mvn clean package

# 3. Configure backend service (systemd)
sudo tee /etc/systemd/system/shopify-backend.service <<EOF
[Unit]
Description=Shopify Backend
After=syslog.target
[Service]
User=ubuntu
Environment="SHOPIFY_API_KEY=your_key"
Environment="SHOPIFY_API_SECRET=your_secret"
ExecStart=/usr/bin/java -jar target/shopify-app-*.jar
[Install]
WantedBy=multi-user.target
EOF

# 4. Build frontend and configure Nginx
cd frontend
npm install && npm run build
sudo cp -r build/* /var/www/html/
sudo systemctl restart nginx

# 5. Enable HTTPS
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

### Option 2: Cloud Services
```env
# Backend Environment Variables
SHOPIFY_API_KEY=your_api_key
SHOPIFY_API_SECRET=your_api_secret

# Frontend Environment Variables
REACT_APP_BACKEND_URL=your_backend_url
REACT_APP_SHOPIFY_API_KEY=your_api_key
```