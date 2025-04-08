# For list of PHP versions see: https://hub.docker.com/r/phpstorm/php-apache/tags
FROM phpstorm/php-apache:8.2-xdebug3.2

RUN pecl install opentelemetry \
	&& docker-php-ext-enable opentelemetry
