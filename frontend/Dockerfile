FROM nginx:1.27-alpine

# nginx 설정 복사
COPY nginx.conf /etc/nginx/nginx.conf

# 정적 파일 복사
COPY index.html /usr/share/nginx/html/
COPY create.html /usr/share/nginx/html/
COPY css/ /usr/share/nginx/html/css/
COPY js/ /usr/share/nginx/html/js/

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
