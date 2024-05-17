package example

import zio._
import zio.stream.{ZSink, ZStream}
import zio.http._
import zio.http.Server.RequestStreaming
import zio.nio.file.{ Files, Path }
import java.io.FileOutputStream
import java.security.MessageDigest

val tmpDir = Path(sys.env("TMPDIR"))

object MultipartFormDataStreaming extends ZIOAppDefault {

    case class Identifier(value: String)

    case class Blob_Data(filename: String, data: ZStream[Any, Nothing, Byte])
    case class FormFields_Data(identifier: Option[Identifier], blob:Option[Blob_Data])
    
    case class Blob_Path(filename: String, path: Path)
    case class FormFields_Path(identifier: Option[Identifier], blob:Option[Blob_Path])

    def bytesToHex (bytes: Array[Byte]): String = bytes.map("%02X" format _).mkString

    def doSomethingWithTheData (identifier: String, filename: String, data: ZStream[Any, Nothing, Byte]): Task[String] =
        //  `identifier` and `filename` are not used in this sample code, but in the actual code
        //  all three fields are needed to correctly process the request
        data
        .run(ZSink.digest(MessageDigest.getInstance("SHA-256")))
        .map(chunk => bytesToHex(chunk.toArray).toLowerCase)

    val app: Routes[Any, Nothing] = Routes(
        Method.POST / "data" -> handler: (request: Request) =>
            request.body.asMultipartFormStream
            .flatMap(streamingForm => streamingForm
                .fields
                .collectZIO(field => field match {
                    case FormField.StreamingBinary("identifier",    contentType, transferEncoding,      filename,  data) =>
                        data.run(ZSink.collectAll[Byte]).map(_.toArray).map(bytes => Identifier(bytesToHex(bytes)))
                    case FormField.StreamingBinary("blob",          contentType, transferEncoding, Some(filename), data) =>
                        ZIO.succeed(Blob_Data(filename, data))
                })
                .runFoldZIO(FormFields_Data(None, None))((result, field) => field match {
                    case Identifier(value)          =>  if result.identifier == None
                                                        then ZIO.succeed(FormFields_Data(Some(Identifier(value)), result.blob))
                                                        else ZIO.fail(new Exception(s"Parameter 'identifier' specified more than once"))
                    case Blob_Data(filename, data)  =>  if result.blob == None
                                                        then ZIO.succeed(FormFields_Data(result.identifier, Some(Blob_Data(filename, data))))
                                                        else ZIO.fail(new Exception(s"Parameter 'blob' specified more than once"))
                })
                .flatMap(formData => formData match {
                    case FormFields_Data(Some(Identifier(identifier)), Some(Blob_Data(filename, data))) =>
                        doSomethingWithTheData(identifier, filename, data)
                    case _ =>
                        ZIO.fail(new Exception(s"Missing either/both 'blob', 'identifier' fields"))
                })
            )
            .map(hash => Response.text(hash))
        ,
        Method.POST / "path" -> handler: (request: Request) =>
            ZIO.scoped:
                request.body.asMultipartFormStream
                .flatMap(streamingForm => streamingForm
                    .fields
                    .collectZIO(field => field match {
                        case FormField.StreamingBinary("identifier",    contentType, transferEncoding,      filename,  data) =>
                            data.run(ZSink.collectAll[Byte]).map(_.toArray).map(bytes => Identifier(bytesToHex(bytes)))
                        case FormField.StreamingBinary("blob",          contentType, transferEncoding, Some(filename), data) =>
                            Files.createTempFileInScoped(dir=tmpDir, suffix=".tmp", prefix=None, fileAttributes = Nil)
                            .flatMap(tmpFile => data
                                .run(ZSink.fromOutputStream(new FileOutputStream(tmpFile.toFile)))
                                .map(_ => Blob_Path(filename, tmpFile))
                            )
                    })
                    .runFoldZIO(FormFields_Path(None, None))((result, field) => field match {
                        case Identifier(value)          =>  if result.identifier == None
                                                            then ZIO.succeed(FormFields_Path(Some(Identifier(value)), result.blob))
                                                            else ZIO.fail(new Exception(s"Parameter 'identifier' specified more than once"))
                        case Blob_Path(filename, path)  =>  if result.blob == None
                                                            then ZIO.succeed(FormFields_Path(result.identifier, Some(Blob_Path(filename, path))))
                                                            else ZIO.fail(new Exception(s"Parameter 'blob' specified more than once"))
                    })
                    .flatMap(formData => formData match {
                        case FormFields_Path(Some(Identifier(identifier)), Some(Blob_Path(filename, path))) =>
                            Files.readAllBytes(path)
                            .map(ZStream.fromChunk)
                            .flatMap(data => doSomethingWithTheData(identifier, filename, data))
                        case _ =>
                            ZIO.fail(new Exception(s"Missing either/both 'blob', 'identifier' fields"))
                    })
                )
                .map(hash => Response.text(hash))
        ,
    ).sandbox @@ Middleware.debug

    private def program: ZIO[Server, Throwable, Unit] =
        for {
            port <- Server.install(app)
            _    <- ZIO.logInfo(s"Server started on port $port")
            _    <- ZIO.never
        } yield ()

    override def run: ZIO[Any & ZIOAppArgs & Scope, Any, Any] =
        program
            .provide(
                ZLayer.succeed(Server.Config.default.requestStreaming(RequestStreaming.Enabled)),
                Server.live,
            )
}