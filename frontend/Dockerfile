# Stage 1: Compile and Build angular codebase
FROM node:14-alpine as build

# Устанавливаем зависимости для сборки нативных модулей (node-gyp)
RUN apk add --no-cache python3 make g++

# Устанавливаем рабочую директорию
WORKDIR /usr/local/app

# Копируем только файлы конфигурации пакетов (для кэширования Docker)
COPY package*.json ./

# Устанавливаем зависимости (с игнорированием конфликтов версий)
RUN npm install --legacy-peer-deps

# Копируем остальной исходный код
COPY ./ ./

# Увеличиваем лимит памяти для Node.js и запускаем сборку
ENV NODE_OPTIONS="--max_old_space_size=4096"
RUN npm run build -- --configuration production


# Stage 2: Serve app with nginx server
FROM nginx:1.23.1-alpine

# Копируем конфигурацию nginx
COPY /.nginx/default.conf /etc/nginx/conf.d/default.conf

# Копируем скомпилированные файлы из первого этапа
COPY --from=build /usr/local/app/dist/utoronto /usr/share/nginx/html

# Открываем порт 80
EXPOSE 80