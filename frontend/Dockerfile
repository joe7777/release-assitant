# Build stage
FROM node:20-alpine AS builder
WORKDIR /app

ARG VITE_LLM_HOST_URL
ENV VITE_LLM_HOST_URL=${VITE_LLM_HOST_URL}

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# Runtime stage
FROM nginx:stable-alpine
COPY --from=builder /app/dist/ /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
