package forj.http.testing;

import forj.http.HttpApp;
import forj.http.Request;
import forj.http.Response;
import forj.http.Routes;
import forj.typeclass.Effect;

/**
 * Runs an app or routes on a request, with no server: routes are functions, so a test is a
 * function call.
 *
 * <pre>{@code
 * Response r = HttpTest.run(routes, Request.get("/users/1"));
 * assertThat(r).hasStatus(Status.OK).hasBody("ana");
 * }</pre>
 */
public final class HttpTest {
    private HttpTest() {}

    /** Runs {@code app} on {@code request} and waits for the response. The app's errors are rethrown as they are. */
    public static <F<_>> Response run(HttpApp<F> app, Request request)(using Effect<F> effect) {
        try {
            return effect.unsafeRunSync(app.run(request));
        } catch (Exception e) {
            throw HttpTest.<RuntimeException>sneaky(e);
        }
    }

    /** Runs {@code routes} as an app: a request no route matches gets 404. */
    public static <F<_>> Response run(Routes<F> routes, Request request)(using Effect<F> effect) {
        return run(HttpApp.of(routes)(using effect), request)(using effect);
    }

    /** Assertions on a response. */
    public static ResponseAssert assertThat(Response response) {
        return new ResponseAssert(response);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneaky(Throwable e) throws E {
        throw (E) e;
    }
}
