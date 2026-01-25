-- Seed initial entity definitions for Davis Vantage Pro sensors
-- These can be extended later for other data sources

INSERT INTO entities (entity_id, friendly_name, source, unit_of_measurement, device_class, aggregation_method) VALUES
-- Temperature sensors
('sensor.davis_temp_out', 'Outside Temperature', 'davis', '°C', 'temperature', 'mean'),
('sensor.davis_temp_in', 'Inside Temperature', 'davis', '°C', 'temperature', 'mean'),

-- Humidity sensors
('sensor.davis_humidity_out', 'Outside Humidity', 'davis', '%', 'humidity', 'mean'),
('sensor.davis_humidity_in', 'Inside Humidity', 'davis', '%', 'humidity', 'mean'),

-- Pressure
('sensor.davis_pressure', 'Barometric Pressure', 'davis', 'hPa', 'pressure', 'mean'),

-- Wind
('sensor.davis_wind_speed', 'Wind Speed', 'davis', 'km/h', 'wind_speed', 'mean'),
('sensor.davis_wind_gust', 'Wind Gust', 'davis', 'km/h', 'wind_speed', 'max'),
('sensor.davis_wind_dir', 'Wind Direction', 'davis', '°', NULL, 'mean'),

-- Rain
('sensor.davis_rain_rate', 'Rain Rate', 'davis', 'mm/h', 'precipitation_intensity', 'max'),
('sensor.davis_rain_daily', 'Rain Today', 'davis', 'mm', 'precipitation', 'last'),
('sensor.davis_rain_storm', 'Storm Rain', 'davis', 'mm', 'precipitation', 'last'),

-- Solar/UV
('sensor.davis_solar_radiation', 'Solar Radiation', 'davis', 'W/m²', 'irradiance', 'mean'),
('sensor.davis_uv_index', 'UV Index', 'davis', 'UV', NULL, 'max'),

-- Derived
('sensor.davis_dewpoint', 'Dew Point', 'davis', '°C', 'temperature', 'mean'),
('sensor.davis_heat_index', 'Heat Index', 'davis', '°C', 'temperature', 'max'),
('sensor.davis_wind_chill', 'Wind Chill', 'davis', '°C', 'temperature', 'min'),

-- METAR
('sensor.metar_temp', 'METAR Temperature', 'metar', '°C', 'temperature', 'mean'),
('sensor.metar_dewpoint', 'METAR Dew Point', 'metar', '°C', 'temperature', 'mean'),
('sensor.metar_pressure', 'METAR Pressure', 'metar', 'hPa', 'pressure', 'mean'),
('sensor.metar_visibility', 'METAR Visibility', 'metar', 'm', NULL, 'mean'),
('sensor.metar_raw', 'METAR Raw String', 'metar', NULL, NULL, 'last');

-- Seed entity definitions for OpenWeather API

('sensor.openweather_wind_dir', 'OpenWeather Wind Direction', 'openweather', '°', NULL, 'mean'),
('sensor.openweather_clouds', 'OpenWeather Cloud Coverage', 'openweather', '%', NULL, 'mean'),
('sensor.openweather_weather_id', 'OpenWeather Condition ID', 'openweather', NULL, NULL, 'last'),
('sensor.openweather_weather_icon', 'OpenWeather Icon', 'openweather', NULL, NULL, 'last');
