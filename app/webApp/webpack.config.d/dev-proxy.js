// wasmJsBrowserDevelopmentRun(webpack dev server) 전용 설정. 배포 번들에는 영향이 없다.
//
// 개발 루프에서 웹은 dev server가, API는 로컬 Spring(8080)이 담당한다. 클라이언트는
// window.location.origin(= dev server) 으로 /api 를 호출하므로, 그 요청을 로컬 Spring으로
// 프록시해야 같은 오리진처럼 동작한다(토큰 호스트 스코프도 origin 기준이라 정합).
//
// ⚠️ webpack devServer 기본 포트도 8080이라 로컬 Spring(8080)과 충돌한다 — dev server는
//    반드시 다른 포트(8081)를 명시한다. (호스트 스코프는 host만 비교하므로 포트가 달라도 무방.)
// ⚠️ proxy 스키마는 webpack-dev-server 버전에 따라 객체/배열로 갈린다. v4+는 배열 형태다.
//    구동 시 콘솔에 스키마 경고가 뜨면 이 형태를 조정한다.
config.devServer = Object.assign({}, config.devServer, {
    port: 8081,
    proxy: [
        {
            context: ["/api"],
            target: "http://localhost:8080",
            changeOrigin: true,
        },
    ],
});
