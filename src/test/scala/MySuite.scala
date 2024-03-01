package example

import zio.{ Chunk, Task, ZIO, durationInt }
import zio.nio.file.{ Files, Path }
import zio.http.{ Body, Boundary, Form, FormField, Header, Headers, MediaType, Method, Request, Root, Server, URL, Version }
import zio.stream.ZStream
import zio.test.{ ZIOSpecDefault, assertTrue, assert, TestAspect }
import zio.test.Assertion.{ nothing }
import zio.test.TestResult.{ allSuccesses }
import zio.ZLayer
import scala.util.boundary
import zio.http.Server.RequestStreaming


val blob_1K     = "0dfba6266bcebf53a0ed863f5df4edf56066e6a5194df242a2b31f13bf7bb9f8"
val blob_2K     = "8960c75f721872b381f4e81ca7219bd268a47019e019264de3418088e4b1fbb0"
val blob_3K     = "629c90e7b104b8d7de31bbc3aabd05305451b9684e1d5ab38e49ba7bb63f4396"
val blob_4K     = "0d37b6cba13b88d803e866241fcace8b7f1dad25156f8f2383f914f3f54fc51e"
val blob_5K     = "31bae7cdae1214f650a92f0731e7af0ed3d8805a0f883ef5031fd2b77c77f6c4"
val blob_6K     = "6bb01cc243776c588ee34ee283043db4575ee1cf7fff838a1ead9f3ee8b785f0"
val blob_7K     = "5d134b0d55a3efc8434849cbd8136ebf4730f33d6688b7397ef1a14d66c003ef"
val blob_8K     = "35a2870c8031ff6eb2357611dde0cdab009105d9627858c7857d8c1d98f52a4c"
val blob_9K     = "0c03d7fcf61a5b338f462a39f56f6556a106a68bd488604183c97f09b26724aa"

val blob_10K    = "90a6bbdfc71693e18e021906479463d5d685fd3661ee00c91d31297968c36331"
val blob_100K   = "36ae43f85e706511dbabc8dc38cc0b3fe737f9a5cf7c3d11b6e36889634073a2"
val blob_200K   = "35d7eb0d88cbcad1779e592d3b0b59e61ab9818890283b3a1cb9cb32175d6733"
val blob_300K   = "9ae5235637f049c02988d4a5cf5a321c7246a8b7bf133eeb69d11095e3bb2aad"
val blob_400K   = "eaa8eea0ac6540ab1d021f436599f48a9f69bda37ea2841acd3ba1184dd639b4"

val blob_1M     = "4073041693a9a66983e6ffb75b521310d30e6db60afc0f97d440cb816bce7c63"

object BlobSpec extends ZIOSpecDefault:
    def readSampleBlob (blobHash: String): Task[ZStream[Any, Nothing, Byte]] = Files.readAllBytes(Path(s"src/test/resources/blobs/${blobHash}.blob")).map(ZStream.fromChunk)

    def post (identifier: String, boundary: Boundary, data: ZStream[Any, Nothing, Byte]) = Request(
        url = URL(Root / "upload"),
        method = Method.POST,
        body = Body.fromStream(
            Form(
                FormField.textField(name="identifier", value="", mediaType = MediaType.text.`plain`),
                FormField.StreamingBinary(
                      name = "blob"
                    , data = data
                    , filename = Some(identifier)
                    , contentType = MediaType.application.`octet-stream`
                ),
            )
            .multipartBytes(boundary)
        ).contentType(newMediaType = MediaType.multipart.`form-data`, newBoundary = boundary),
        headers = Headers(Header.ContentType(MediaType.multipart.`form-data`)),
        version = Version.Http_1_1,
    )

    def spec = suite("BlobApis")(
        test("POST large blob") {
            // val file_signature = blob_8K
            val file_signature = blob_1M
            for {
                boundary <- Boundary.randomUUID
                content  <- readSampleBlob(file_signature)
                response <- MultipartFormDataStreaming.app.runZIO(post(file_signature, boundary, content))
                body     <- response.body.asString
            } yield allSuccesses(
                assertTrue(response.status.code == 200),
                // assertTrue(body == "8274")
                assertTrue(body == file_signature)
            )
        } @@ TestAspect.timeout(10.second),
    ).provideLayerShared(environment)
    @@ TestAspect.sequential
    @@ TestAspect.timeout(30.second)

    val environment: ZLayer[Any, Throwable, Unit] =
        ZLayer.succeed(Server.Config.default.requestStreaming(RequestStreaming.Enabled))

