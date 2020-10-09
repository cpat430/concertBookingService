package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.*;
import se325.assignment01.concert.common.types.BookingStatus;
import se325.assignment01.concert.service.domain.*;
import se325.assignment01.concert.service.jaxrs.LocalDateTimeParam;
import se325.assignment01.concert.service.mapper.BookingMapper;
import se325.assignment01.concert.service.mapper.ConcertMapper;
import se325.assignment01.concert.service.mapper.PerformerMapper;
import se325.assignment01.concert.service.mapper.SeatMapper;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static se325.assignment01.concert.service.util.TheatreLayout.NUM_SEATS_IN_THEATRE;

@Path("/concert-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConcertResource {

    /*
     * Used pessimistic locking for the concert resource. This means that all of the read locks can all be obtained
     * and they can all read if they need to. However, once there is a write lock, then no one can read. This was used
     * to stop reading data that is being written to so there is no repeatable reads.
     *
     * Eager fetching is the used for the majority of the time because it is a concert service and it pays for the
     * database to have all the entities loaded for the users to look at. A drawback to eager is that it will get
     * all the data from all of the concerts such as the seats, performers and this can be very computationally taxing
     * as the user may not decide to book from any of those.
     *
     * Maybe it would be better to use lazy loading for things like the concerts, performers, bookings, seats etc.
     * This will make it more efficient as it will only load the seats etc when the concerts are clicked on.
     *
     * In future it would be good to try using optimistic
     */

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);
    private static final ConcurrentHashMap<LocalDateTime, LinkedList<SubscriptionInfo>> subsInfo = new ConcurrentHashMap<>();

    private static final String AUTH_COOKIE = "auth";

    PersistenceManager persistenceManager = PersistenceManager.instance();

    /**
     * - POST    <base-uri>/login
     * Logs the user into the database. If the user doesn't exist in the database,
     * then there will be a status of 401 returned.
     * @param user
     * @return
     */
    @POST
    @Path("/login")
    public Response login(UserDTO user) {

        // convert the dto user to a domain user
        User domainUser = null;

        // check if the user exists in the database
        EntityManager em = persistenceManager.createEntityManager();

        try {

            em.getTransaction().begin();

            // find the user in the database
            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.username = :username and u.password = :password", User.class);
            userQuery.setParameter("username", user.getUsername());
            userQuery.setParameter("password", user.getPassword());
            userQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            domainUser = userQuery.getSingleResult();

            em.getTransaction().commit();

            // check if the user was found
            if (domainUser == null) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }

            NewCookie newCookie = newSession(domainUser, em);
            LOGGER.info("Generated a new cookie for the new user");

            return Response
                    .ok(domainUser)
                    .cookie(newCookie)
                    .build();
        } catch (NoResultException e) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - GET    <base-uri>/concerts/summaries
     * Retrieves the Concert summaries. The HTTP response
     * message has a status code of either 200 or 404, depending on
     * whether the concert summaries are found.
     * @return
     */
    @GET
    @Path("/concerts/summaries")
    public Response retrieveConcertSummaries() {

        // get all the concerts from the database
        List<ConcertSummaryDTO> concertSummaries = new ArrayList<>();

        EntityManager em = persistenceManager.createEntityManager();

        try {
            em.getTransaction().begin();

            // get the concerts
            TypedQuery<Concert> concertQuery = em.createQuery("select c from Concert c", Concert.class);
            concertQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Concert> concerts = concertQuery.getResultList();
            LOGGER.info("retrieving the concert summaries");

            // if there are no concerts throw a not found
            if (concerts.isEmpty()) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            em.getTransaction().commit();

            // iterate through and map concerts to summaries (dto)
            for (Concert c : concerts) {
                concertSummaries.add(ConcertMapper.toConcertSummaryDto(c));
            }
        } finally {
            commitIfActive(em);
            em.close();
        }

        return Response
                .ok(concertSummaries)
                .build();
    }

    /**
     * - GET    <base-uri>/concerts/{id}
     * Retrieves a Concert based on its unique id. The HTTP response
     * message has a status code of either 200 or 404, depending on
     * whether the specified Concert is found.
     * @param id
     * @return
     */
    @GET
    @Path("/concerts/{id}")
    public Response retrieveConcert(@PathParam("id") long id) {

        EntityManager em = persistenceManager.createEntityManager();
        try {

            // start a new transaction
            em.getTransaction().begin();

            // use the entity manager to retrieve em.find(), delete em.merge() or persist em.persist()
            Concert concert = em.find(Concert.class, id, LockModeType.PESSIMISTIC_READ);

            // commit the new transaction
            em.getTransaction().commit();

            if (concert == null) {
                // Return a HTTP 404 response if the specified Concert isn't found.
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            return Response
                    .ok(ConcertMapper.toConcertDto(concert))
                    .build();
        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - GET    <base-uri>/concerts
     * Retrieves all Concerts. The HTTP response
     * message has a status code of 200.
     * @return
     */
    @GET
    @Path("/concerts")
    public Response retrieveAllConcerts() {

        EntityManager em = persistenceManager.createEntityManager();

        List<ConcertDTO> dtoConcerts = new ArrayList<>();

        // query to get all concerts and put it all in a list of concert dto's
        try {

            em.getTransaction().begin();
            TypedQuery<Concert> concertQuery = em.createQuery("select c from Concert c", Concert.class);
            concertQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Concert> concerts = concertQuery.getResultList();

            em.getTransaction().commit();

            for (Concert c : concerts) {
                dtoConcerts.add(ConcertMapper.toConcertDto(c));
            }

            return Response
                    .ok(dtoConcerts)
                    .build();
        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - GET    <base-uri>/performers/{id}
     * Retrieves a Performer based on its unique id. The HTTP response
     * message has a status code of either 200 or 404, depending on
     * whether the specified Performer is found.
     * @param id
     * @return
     */
    @GET
    @Path("/performers/{id}")
    public Response retrievePerformer(@PathParam("id") long id) {

        EntityManager em = persistenceManager.createEntityManager();
        try {

            // start a new transaction
            em.getTransaction().begin();

            // use the entity manager to retrieve em.find(), delete em.merge() or persist em.persist()
            Performer performer = em.find(Performer.class, id, LockModeType.PESSIMISTIC_READ);

            // commit the new transaction
            em.getTransaction().commit();

            if (performer == null) {
                // Return a HTTP 404 response if the specified Concert isn't found.
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            return Response
                    .ok(PerformerMapper.toPerformerDTO(performer))
                    .build();
        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - GET    <base-uri>/performers
     * Retrieves all Performers. The HTTP response
     * message has a status code of 200.
     * @return
     */
    @GET
    @Path("/performers")
    public Response retrieveAllPerformers() {

        EntityManager em = persistenceManager.createEntityManager();

        List<PerformerDTO> dtoPerformers = new ArrayList<>();

        // query to get all performers and put it all in a list of concert dto's
        try {

            em.getTransaction().begin();
            TypedQuery<Performer> performerQuery = em.createQuery("select p from Performer p", Performer.class);
            performerQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Performer> performers = performerQuery.getResultList();

            em.getTransaction().commit();

            for (Performer p : performers) {
                dtoPerformers.add(PerformerMapper.toPerformerDTO(p));
            }

            GenericEntity<List<PerformerDTO>> entity = new GenericEntity<List<PerformerDTO>>(dtoPerformers) {};

            return Response
                    .ok(entity)
                    .build();
        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - GET    <base-uri>/bookings
     * Retrieves all booking from a specific user. The HTTP response
     * message has a status code of either 200 or 401, depending on
     * whether the user is authenticated or not.
     * @param cookieId
     * @return
     */
    @GET
    @Path("/bookings")
    public Response retrieveUserBookings(@CookieParam(AUTH_COOKIE) Cookie cookieId) {

        // check if the user is authenticated
        if (cookieId == null || cookieId.getValue().equals("")) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        // get the user UUID from the cookie
        String userUuid = cookieId.getValue();

        List<BookingDTO> dtoBookList = new ArrayList<BookingDTO>();

        EntityManager em = persistenceManager.createEntityManager();

        try {

            em.getTransaction().begin();

            // get all the bookings
            TypedQuery<Booking> bookingQuery = em.createQuery("select b from Booking b", Booking.class);
            bookingQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Booking> bookings = bookingQuery.getResultList();

            em.getTransaction().commit();

            // iterate through the bookings and return all that are the users.
            for (Booking booking : bookings) {

                // if the booking uuid is the same as the cooking uuid
                if (booking.getUuid().equals(userUuid)) {
                    dtoBookList.add(BookingMapper.toBookingDTO(booking));
                }
            }

            GenericEntity<List<BookingDTO>> entity = new GenericEntity<List<BookingDTO>>(dtoBookList) {};

            return Response
                    .ok(entity)
                    .build();

        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - GET    <base-uri>/bookings/{id}
     * Retrieves a booking based on its unique id. The HTTP response
     * message has a status code of either 200, 403, or 404, depending
     * on whether the booking is found or if the user is authenticated.
     * @param id
     * @param cookieId
     * @return
     */
    @GET
    @Path("/bookings/{id}")
    public Response retrieveBookingById(@PathParam("id") long id, @CookieParam(AUTH_COOKIE) Cookie cookieId) {

        String userUuid = cookieId.getValue();

        EntityManager em = persistenceManager.createEntityManager();

        try {

            // get all bookings with the concert id

            em.getTransaction().begin();

            Booking booking = em.find(Booking.class, id, LockModeType.PESSIMISTIC_READ);

            em.getTransaction().commit();


            // check if the booking is there
            if (booking == null) {
                LOGGER.info("booking was null");
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            LOGGER.info("booking was not null");

            // check that the cookie id and the booking is the same
            if (!(booking.getUuid().equals(userUuid))) {
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            // if it exists and the user is correct, then convert to bookingDTO
            BookingDTO dtoBooking = BookingMapper.toBookingDTO(booking);

            GenericEntity<BookingDTO> entity = new GenericEntity<BookingDTO>(dtoBooking) {};

            return Response
                    .ok(entity)
                    .build();
        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - POST    <base-uri>/bookings
     * Attempts a booking. The HTTP response
     * message has a status code of either 201, 400, 401, 403
     * depending on whether the user is authenticated,
     * if the concert or date is wrong, or if the number
     * of seats requested don't exist.
     * @param brqDTO
     * @param cookieId
     * @param uriInfo
     * @return
     */
    @POST
    @Path("/bookings")
    public Response attemptBooking(BookingRequestDTO brqDTO, @CookieParam(AUTH_COOKIE) Cookie cookieId, @Context UriInfo uriInfo) {

        // can book if they are authorised
        LOGGER.info("Checking if the user is authorised");

        // they are not authorised
        if (cookieId == null || cookieId.getValue().equals("")) {
            LOGGER.info("User is not authorised");
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        long concertId = brqDTO.getConcertId();
        LocalDateTime date = brqDTO.getDate();
        List<String> seatLabels = brqDTO.getSeatLabels();

        String userUuid = cookieId.getValue();

        // if there are no selected seats.
        if (seatLabels.isEmpty()) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        // from this point onwards, they are authorised //

        EntityManager em = persistenceManager.createEntityManager();

        try {

            em.getTransaction().begin();

            // check if the concert id exists
            Concert concert = em.find(Concert.class, concertId, LockModeType.PESSIMISTIC_READ);

            em.getTransaction().commit();

            // if the concert doesn't exist, return a bad request
            if (concert == null) {
                LOGGER.info("couldn't find concert");
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }

            boolean good = false;

            for (LocalDateTime concertDate : concert.getDates()) {
                // check if the concert date is valid from the booking request
                if (concertDate.equals(date)) {
                    good = true;
                }
            }

            // if the date is wrong, throw a bad request
            if (!good) {
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }

            // start a new transaction to book the seats
            em.getTransaction().begin();

            // check if the seats are available
            TypedQuery<Seat> seatQuery = em.createQuery("select s from Seat s where s.label in :label and s.date = :date and s.isBooked = false", Seat.class);
            seatQuery.setParameter("label", seatLabels);
            seatQuery.setParameter("date", date);
            seatQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE);
            List<Seat> freeRequestedSeats = seatQuery.getResultList();

            LOGGER.info("Number of free seats: " + freeRequestedSeats.size());

            // check if there were any booked seats
            if (freeRequestedSeats.size() != seatLabels.size()) {
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            // from here the seats are free

            Set<Seat> seatsSet = new HashSet<>();

            // iterate through the free seats and set them to booked, and add to the seat set
            for (Seat s : freeRequestedSeats) {
                s.setBooked(true);
                seatsSet.add(s);
            }

            // if we reach here, then the seats are valid and they have been booked

            // create a booking (id, date, seats)
            Booking booking = new Booking(concertId, date, seatsSet);
            booking.setUuid(userUuid);

            // add to the database
            em.persist(booking);

            // get the number of available seats for the notification
            int freeSeats = em.createQuery("SELECT COUNT(s) FROM Seat s WHERE s.date = :date AND s.isBooked = false", Long.class)
                    .setParameter("date", date)
                    .getSingleResult()
                    .intValue();

            LOGGER.info("Number of free seats in total: " + freeSeats);

            // check whether the number of seats exceeds a percentage for the subscribers
            checkWithSubscribers(concertId, date, freeSeats);

            em.getTransaction().commit();

            return Response
                    .created(URI.create(uriInfo.getBaseUri() + "concert-service/bookings/" + booking.getBookingId()))
                    .build();

        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - GET    <base-uri>/seats/{date}
     * Retrieves specified seats on a particular date. The HTTP response
     * message has a status code of either 200.
     * @param date
     * @param status
     * @return
     */
    @GET
    @Path("/seats/{date}")
    public Response retrieveSpecifiedSeatsOnDate(@PathParam("date") LocalDateTimeParam date, @QueryParam("status") BookingStatus status) {

        // return a list of seatDTO
        List<SeatDTO> seatDTOList = new ArrayList<SeatDTO>();
        LocalDateTime curDate = date.getLocalDateTime();

        // get the seats from the entity manager
        EntityManager em = persistenceManager.createEntityManager();

        try {

            em.getTransaction().begin();

            // get all the seats
            TypedQuery<Seat> seatQuery = em.createQuery("select s from Seat s where s.date=:date", Seat.class);
            seatQuery.setParameter("date", curDate);
            seatQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Seat> seats = seatQuery.getResultList();

            LOGGER.info("Number of seats retrieved: " + seats.size());

            em.getTransaction().commit();

            // iterate through the list and convert to seatDTO
            for (Seat s : seats) {

                if (status.equals(BookingStatus.Any) ||
                        (status.equals(BookingStatus.Booked) && s.isBooked()) ||
                        (status.equals(BookingStatus.Unbooked) && !s.isBooked())) {
                    seatDTOList.add(SeatMapper.toSeatDto(s));
                }
            }

            GenericEntity<List<SeatDTO>> entity = new GenericEntity<List<SeatDTO>>(seatDTOList) {};

            return Response
                    .ok(entity)
                    .build();
        } finally {
            commitIfActive(em);
            em.close();
        }
    }

    /**
     * - POST    <base-uri>/subscribe/concertInfo
     * Subscribes a user to a concert and will be notified if the
     * booked seat capacity exceeds a certain percentage. The HTTP
     * response message has a status code of either 200, 400, or 403,
     * depending on whether the user is authenticated, if the concert
     * exists in the database and the date is correct.
     * @param subInfo
     * @param sub
     * @param cookieId
     * @return
     */
    @POST
    @Path("/subscribe/concertInfo")
    public void subscribeToConcert(ConcertInfoSubscriptionDTO subInfo, @Suspended AsyncResponse sub, @CookieParam(AUTH_COOKIE) Cookie cookieId) {

        // check if the concert date exists
        EntityManager em = persistenceManager.createEntityManager();

        try {

            LOGGER.info("check if the user is logged in");

            // check if the user is the correct user
            User user = getLoggedInUser(cookieId, em);

            if (user == null) {
                sub.resume(Response.status(Response.Status.UNAUTHORIZED).build());
                return;
            }

            LOGGER.info("User is logged in");

            em.getTransaction().begin();

            Concert concert = em.find(Concert.class, subInfo.getConcertId(), LockModeType.PESSIMISTIC_READ);

            // no concert found or the concert doesn't contain the date of the subscription
            if (concert == null || !concert.getDates().contains(subInfo.getDate())) {
                sub.resume(Response.status(Response.Status.BAD_REQUEST).build());
                return;
            }

            LOGGER.info("Concert found in the database");

            em.getTransaction().commit();
        } finally {
            em.close();
        }

        // check if the map contains the date, if not, create a new linked list
        synchronized (subsInfo) {
            if (!subsInfo.containsKey(subInfo.getDate())) {
                subsInfo.put(subInfo.getDate(), new LinkedList<>());
            }
        }

        // create a new subscription info object with the information
        subsInfo.get(subInfo.getDate()).add(new SubscriptionInfo(sub, subInfo));

        LOGGER.info("added the subscription for date: " + subInfo.getDate());
    }

    /**
     * Gets the logged in user from the database. If they don't exist,
     * then the function returns null otherwise the user is returned.
     * @param cookie
     * @param em
     * @return
     */
    private User getLoggedInUser(Cookie cookie, EntityManager em) {
        if (cookie == null) {
            return null;
        }

        User found = null;
        em.getTransaction().begin();

        try {

            // get the user with the uuid (cookie value)
            TypedQuery<User> userQuery = em.createQuery("SELECT u FROM User u where u.uuid = :uuid", User.class);
            userQuery.setParameter("uuid", UUID.fromString(cookie.getValue()));
            userQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            found = userQuery.getSingleResult();

        } catch(NoResultException e) {
            // if there is no result, do nothing, output will be null
            LOGGER.info("please log in");
        }

        em.getTransaction().commit();

        return found;
    }

    /**
     * checks against all the subscribers for a specific date whether the
     * percentage of available seats exceeds their specified date they
     * required for a notification. If so, a notification is sent to
     * each user.
     * @param concertId
     * @param date
     * @param availableSeats
     */
    private void checkWithSubscribers(long concertId, LocalDateTime date, int availableSeats) {

        // get the percentage from the available seats
        int percentageBooked = 100 - (int)(((double)availableSeats / NUM_SEATS_IN_THEATRE) * 100);

        LOGGER.info("Percentage booked for the " + date.toString() + ": " + percentageBooked + "%");

        // if the key doesn't exist, then return because there are no subscribers for that date/concert
        if (!subsInfo.containsKey(date)) {
            LOGGER.info("the map doesn't contain the specified date: " + date.toString());
            return;
        }

        // iterate through the subscriptions
        for (Iterator<SubscriptionInfo> it = subsInfo.get(date).iterator(); it.hasNext(); ) {
            SubscriptionInfo subscriptionInfo = it.next();

            // check if it is the same concert to prevent notifying someone for the same concert.
            if (subscriptionInfo.getSubInfo().getConcertId() != concertId) {
                LOGGER.info("This is the wrong date");
                return;
            }

            if (percentageBooked >= subscriptionInfo.getSubInfo().getPercentageBooked()) {
                LOGGER.info("Notifying someone...");

                // remove the subscriber so they are only updated once which is done in O(1)
                it.remove();

                // send out the notification.
                subscriptionInfo.getAsyncResponse().resume(Response.ok(new ConcertInfoNotificationDTO(availableSeats)).build());
            }
        }
    }

    /**
     * creates a new session for a user and assigns them a random
     * UUID to identify them.
     * @param user
     * @param em
     * @return
     */
    private NewCookie newSession(User user, EntityManager em) {
        em.getTransaction().begin();

        user.setUuid(UUID.randomUUID());

        em.getTransaction().commit();

        return new NewCookie(AUTH_COOKIE, user.getUuid().toString());
    }

    /**
     * commits the em if it is still active when it shouldn't be.
     * acts as a safety, just in case.
     * @param em
     */
    private void commitIfActive(EntityManager em) {
        if (em.getTransaction().isActive()) {
            em.getTransaction().commit();
        }
    }
}

