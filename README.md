# Sample project to highlight an issue processing `multipart-form` uploads using `zio-http`

Sample project using `scala 3` (3.4.2), `zio 2` (2.1.2), `zio-http` (3.0.0-RC8) and other ancillary ZIO libraries that highlights a problem processing `multipart-form` data in streaming mode.

## Collecting more fields
I am not sure we are using the best option to collect the different fields from the `StreamingForm` value.
The solution we have identified kind of works, even if it feels pretty clanky.
Any suggestion on how to do this differently is very welcome.

## Processing ZStream data
Depending on how we process the incoming request though, we are getting different results.

### `/path`
If we do consume the full streaming data right away from the `ZStream[FormField]`, we get all the data correctly; this option is implemented in the `/path` handler.
Unfortunately, this means we need to write all the data to a temporary file, and later re-read all the data to actually process its content.

### `/data`
Another option is we keep the ZStream around until we have collected all the needed information, and later process it altogether.
This option is implemented in the `/data` handler; it works file with "small" file uploads (up to ~7KB in size), but hangs with bigger uploads.

# What are we doing wrong?

Any suggestion is very welcome