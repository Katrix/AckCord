package net.katsstuff.ackcord

import net.katsstuff.ackcord.http.requests.Request

class RequestPermissionException(val request: Request[_, _])
    extends Exception(s"Do not have enough permissions to run request: $request")
