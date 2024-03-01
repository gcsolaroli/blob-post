package example

import zio._
import zio.stream.{ZSink, ZStream}
import zio.http._
import zio.http.Server.RequestStreaming
import zio.nio.file.{ Files, Path }
import java.io.FileOutputStream
import java.security.MessageDigest

object MultipartFormDataStreaming extends ZIOAppDefault {

    // val tmpDir = Path(sys.env("TMPDIR"))
    val tmpDir = Path(sys.env("TMPDIR"))
    val targetDir = Path("target")
    def bytesToHex (bytes: Array[Byte]): String = bytes.map("%02X" format _).mkString.toLowerCase

    val app: HttpApp[Any] = Routes(
        Method.POST / "upload" -> handler: (req: Request) =>
            if (req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`))
            then
                ZIO.debug("Starting to read multipart/form stream")
                *>
                req.body.asMultipartFormStream.map(_.fields)
                .flatMap(fieldStream =>
                    val fieldData: ZStream[Any, Throwable, Byte] = fieldStream.flatMap(field => field match {
                        case sb: FormField.StreamingBinary => sb.data
                        case _                             => ZStream.empty
                    })
                    ZIO.scoped:
                        Files.createTempFileInScoped(dir=tmpDir, suffix=".tmp", prefix=None, fileAttributes = Nil)
                        .tap(tmpFile => ZIO.log(s"TEMP FILE: ${tmpFile}"))
                        .flatMap { tmpFile => fieldData
                            .timeoutFail(new Exception)(Duration.fromMillis(10000))
                            .tapSink(ZSink.fromOutputStream(new FileOutputStream(tmpFile.toFile)))
                            .run(ZSink.digest(MessageDigest.getInstance("SHA-256").nn))
                            .map((chunk: Chunk[Byte]) => bytesToHex(chunk.toArray))
                            .map(hash => (tmpFile, hash))
                        }
                        .flatMap((tmpFile, hash) =>
                            Files.move(tmpFile, targetDir / "test.blob")
                            .map(_ => hash)
                        )
                )
                .map(hash => Response.text(hash))
            else ZIO.succeed(Response(status = Status.NotFound))
    ).sandbox.toHttpApp @@ Middleware.debug

    private def program: ZIO[Server, Throwable, Unit] =
        for {
            port <- Server.install(app)
            _    <- ZIO.logInfo(s"Server started on port $port")
            _    <- ZIO.never
        } yield ()

    override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
        program
            .provide(
                // ZLayer.succeed(Server.Config.default.enableRequestStreaming),
                ZLayer.succeed(Server.Config.default.requestStreaming(RequestStreaming.Enabled)),
                Server.live,
            )
}