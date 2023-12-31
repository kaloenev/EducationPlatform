package com.alibou.security.coursesServiceController;

import com.alibou.security.exceptionHandling.CustomException;
import com.alibou.security.lessons.*;
import com.alibou.security.token.TokenRepository;
import com.alibou.security.user.*;
import com.alibou.security.userFunctions.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseService {
    private final UserRepository repository;
    private final TokenRepository tokenRepository;

    public static double LOWERMEDIAN_PRICE_COURSE;
    public static double UPPERMEDIAN_PRICE_COURSE;
    public static double SUM_PRICE_COURSE;
    public static double COURSES_COUNT;
    public static double BOTTOM20PRICE_COURSE;

    public static double DEVIATION_COURSE = 0;
    public static double BOTTOM40PRICE_COURSE;
    public static double BOTTOM60PRICE_COURSE;
    public static double BOTTOM80PRICE_COURSE;
    public static double LOWERMEDIAN_PRICE_LESSON;
    public static double UPPERMEDIAN_PRICE_LESSON;
    public static double SUM_PRICE_LESSON;
    public static double LESSONS_COUNT;
    public static double BOTTOM20PRICE_LESSON;
    public static double BOTTOM40PRICE_LESSON;
    public static double BOTTOM60PRICE_LESSON;
    public static double BOTTOM80PRICE_LESSON;
    public static double DEVIATION_LESSON = 0;


    private final LessonRepository lessonRepository;

    private final TerminRepo terminRepo;

    private final LessonTerminRepo lessonTerminRepo;

    private final CourseTerminRepo courseTerminRepo;

    private final TeacherRepository teacherRepository;

    private final StudentRepository studentRepository;

    private final ReviewRepo reviewRepo;

    private final ThemaRepository themaRepository;

    public void enrollUserInCourse(String token, int courseID, int terminID) throws CustomException {
        Student student = studentRepository.findStudentByTokens_token(token.substring(7));
        CourseTermin termin = courseTerminRepo.getCourseTerminByTerminID(terminID);
        if (termin.isFull()) {
            throw new CustomException(HttpStatus.FORBIDDEN, "Error course is full");
        }
        termin.enrollStudent(student);
        student.addCourseTermin(termin);
    }

    public void enrollUserInLesson(String token, int courseID, int terminID) throws CustomException {
        Student student = studentRepository.findStudentByTokens_token(token.substring(7));
        LessonTermin termin = lessonTerminRepo.getLessonTerminByTerminID(terminID);
        if (termin.isFull()) {
            throw new CustomException(HttpStatus.FORBIDDEN, "Error course is full");
        }
        termin.enrollStudent(student);
        student.addLessonTermin(termin);
    }

    public void likeCourse(String token, int lessonID) throws CustomException {
        Student student = studentRepository.findStudentByTokens_token(token.substring(7));
        student.saveLessonToLiked(lessonRepository.getLessonByLessonID(lessonID));
    }

    public void dislikeCourse(String token, int lessonID) throws CustomException {
        Student student = studentRepository.findStudentByTokens_token(token.substring(7));
        student.removeLessonsFromLiked(lessonRepository.getLessonByLessonID(lessonID));
    }

    public void publishDraft(String token, int id) throws CustomException {
        Teacher teacher = teacherRepository.findTeacherByTokens_token(token.substring(7));
        Lesson lesson = lessonRepository.getLessonByLessonID(id);
        if (!Objects.equals(lesson.getTeacher().getId(), teacher.getId())) throw new CustomException(HttpStatus.CONFLICT,
                "Не притежавате този курс");
        checkForUnallowedValues(lesson);
        lesson.setDraft(false);
        lessonRepository.save(lesson);
        if (lesson.isPrivateLesson()) addNewPriceLesson(lesson.getPrice());
        else addNewPriceCourse(lesson.getPrice());
    }

    private void checkForUnallowedValues(Lesson lesson) throws CustomException  {
        if (lesson.getTitle() == null || Objects.equals(lesson.getTitle(), "")) throw new CustomException(HttpStatus.CONFLICT,
                "Не сте задали заглавие на курса");
        if (lesson.getLength() < 30) throw new CustomException(HttpStatus.CONFLICT, "Не сте задали дължина на урока");
        if (lesson.getDescription() == null || Objects.equals(lesson.getDescription(), ""))
            throw new CustomException(HttpStatus.CONFLICT, "Не сте задали описание на курса");
        if (lesson.getSubject() == null || Objects.equals(lesson.getSubject(), ""))
            throw new CustomException(HttpStatus.CONFLICT, "Не сте задали описание на курса");
        if (!lesson.isHasTermins()) throw new CustomException(HttpStatus.CONFLICT, "Не сте добавили дати на курса");
        if (lesson.getPrice() <= 0) throw new CustomException(HttpStatus.CONFLICT, "Не сте задали цена на курса");
        if (lesson.getGrade() == null || Objects.equals(lesson.getGrade(), ""))
            throw new CustomException(HttpStatus.CONFLICT, "Не сте задали описание на курса");
        if (lesson.getStudentsUpperBound() <= 0) throw new CustomException(HttpStatus.CONFLICT, "Не сте задали максимален брой ученици");
    }

    public void createCourse(String token, CreateCourseRequest courseRequest, boolean isDraft, boolean isPrivateLesson) throws CustomException {
        Teacher teacher = teacherRepository.findTeacherByTokens_token(token.substring(7));
        if (!teacher.isVerified())
            throw new CustomException(HttpStatus.CONFLICT, "You must be verified to create a course");
        System.out.println(teacher.getId());
        Lesson lesson = Lesson.builder().teacher(teacher).build();
        lesson.setTitle(courseRequest.getTitle());
        lesson.setSubject(courseRequest.getSubject());
        lesson.setGrade(courseRequest.getGrade());
        lesson.setDescription(courseRequest.getDescription());
        lesson.setLength(courseRequest.getLength());
        lesson.setPrivateLesson(isPrivateLesson);
        lesson.setPrice(courseRequest.getPrice());
        lesson.setDraft(isDraft);
        lesson.setHasTermins(false);
        if (!isPrivateLesson) {
            lesson.setStudentsUpperBound(courseRequest.getStudentsUpperBound());
            lessonRepository.save(lesson);
            if (courseRequest.getCourseTerminRequests() != null) {
                for (CourseTerminRequestResponse courseTerminRequest : courseRequest.getCourseTerminRequests()) {
                    List<Thema> themas = new ArrayList<>();
                    if (courseRequest.getThemas() != null) {
                        for (ThemaSimpleResponse themaData : courseRequest.getThemas()) {
                            Thema thema;
                            if (themaData.getDescription() == null) {
                                thema = Thema.builder().title(themaData.getTitle()).build();
                            } else
                                thema = Thema.builder().description(themaData.getDescription()).title(themaData.getTitle()).build();
                            themas.add(thema);
                        }
                    }
                    CourseTermin courseTermin = CourseTermin.builder().dateTime(Timestamp.valueOf(courseTerminRequest.getStartDate()
                                    + " " + courseTerminRequest.getCourseHours() + ":00"))
                            .courseDays(getDaysOfWeek(courseTerminRequest.getCourseDaysNumbers()))
                            .courseHoursNumber(Integer.parseInt(courseTerminRequest.getCourseHours().replace(":", "")))
                            .weekLength(courseTerminRequest.getWeekLength()).studentsUpperBound(courseRequest.getStudentsUpperBound())
                            .lesson(lesson).placesRemaining(courseRequest.getStudentsUpperBound()).build();
                    courseTerminRepo.save(courseTermin);
                    for (Thema thema : themas) {
                        thema.setCourseTermin(courseTermin);
                        themaRepository.save(thema);
                        courseTermin.addThema(thema);
                    }
                    courseTerminRepo.save(courseTermin);
                    lesson.addTermin(courseTermin);
                    lesson.setHasTermins(true);
                }
                lesson.getTermins().sort(Comparator.comparing(Termin::getDateTime));
            }
        } else {
            lesson.setStudentsUpperBound(1);
            lessonRepository.save(lesson);
            if (courseRequest.getCourseTerminRequests() != null) {
                for (LessonTerminRequest privateLessonTermin : courseRequest.getPrivateLessonTermins()) {
                    Thema thema = null;
                    if (courseRequest.getThemas() != null) {
                        if (courseRequest.getThemas()[0].getDescription() == null) {
                            thema = Thema.builder().title(courseRequest.getThemas()[0].getTitle()).build();
                        } else {
                            thema = Thema.builder().description(courseRequest.getThemas()[0].getDescription())
                                    .title(courseRequest.getThemas()[0].getTitle()).build();
                        }
                        themaRepository.save(thema);
                    }
                    String hours = privateLessonTermin.getLessonHours();
                    LessonTermin lessonTermin = LessonTermin.builder().lessonHours(Integer.parseInt(hours.replace(":", "")))
                            .dateTime(Timestamp.valueOf(privateLessonTermin.getDate() + " " + hours + ":00")).thema(thema).build();
                    lessonTerminRepo.save(lessonTermin);
                    lessonTermin.setLesson(lesson);
                    lesson.addTermin(lessonTermin);
                    lesson.setHasTermins(true);
                }
                lesson.getTermins().sort(Comparator.comparing(Termin::getDateTime));
            }
        }
        lessonRepository.save(lesson);
        System.out.println(teacher.getId());
        teacher.addLesson(lesson);

        if (!lesson.isDraft()) {
            if (lesson.isPrivateLesson()) addNewPriceLesson(lesson.getPrice());
            else addNewPriceCourse(lesson.getPrice());
        }
    }

    public void editCourse(String token, int lessonID, CreateCourseRequest courseRequest, boolean isDraft, boolean isPrivateLesson) throws CustomException {
        Teacher teacher = teacherRepository.findTeacherByTokens_token(token.substring(7));
        if (!teacher.isVerified()) throw new CustomException(HttpStatus.CONFLICT, "Трябва да се верифицирате първо");
        Lesson lesson = lessonRepository.getLessonByLessonID(lessonID);
        if (!Objects.equals(lesson.getTeacher().getId(), teacher.getId()))
            throw new CustomException(HttpStatus.FORBIDDEN, "Имате достъп само до Вашите курсове");

        if (!isDraft) checkForUnallowedChanges(lesson, courseRequest);

        lesson.setTitle(courseRequest.getTitle());
        lesson.setSubject(courseRequest.getSubject());
        lesson.setGrade(courseRequest.getGrade());
        lesson.setDescription(courseRequest.getDescription());
        lesson.setLength(courseRequest.getLength());
        lesson.setPrivateLesson(isPrivateLesson);
        lesson.setPrice(courseRequest.getPrice());
        lesson.setDraft(isDraft);
        if (!isPrivateLesson) {
            //TODO Find alternative to removal
            lesson.setStudentsUpperBound(courseRequest.getStudentsUpperBound());
            for (CourseTermin termin : lesson.getCourseTermins()) {
                themaRepository.deleteAll(termin.getThemas());
            }
            terminRepo.deleteAll(lesson.getTermins());
            lesson.removeAllTermins();
            lesson.setHasTermins(false);
            List<Thema> themas = new ArrayList<>();
            if (courseRequest.getThemas() != null) {
                for (ThemaSimpleResponse themaData : courseRequest.getThemas()) {
                    Thema thema;
                    if (themaData.getDescription() == null) {
                        thema = Thema.builder().title(themaData.getTitle()).build();
                    } else
                        thema = Thema.builder().description(themaData.getDescription()).title(themaData.getTitle()).build();
                    themas.add(thema);
                    themaRepository.save(thema);
                    themas.add(thema);
                }
            }
            if (courseRequest.getCourseTerminRequests() != null) {
                for (CourseTerminRequestResponse courseTerminRequest : courseRequest.getCourseTerminRequests()) {
                    CourseTermin courseTermin = CourseTermin.builder().dateTime(Timestamp.valueOf(courseTerminRequest.getStartDate()
                                    + " " + courseTerminRequest.getCourseHours() + ":00"))
                            .courseDays(getDaysOfWeek(courseTerminRequest.getCourseDaysNumbers()))
                            .courseHoursNumber(Integer.parseInt(courseTerminRequest.getCourseHours().replace(":", "")))
                            .weekLength(courseTerminRequest.getWeekLength()).studentsUpperBound(courseRequest.getStudentsUpperBound())
                            .lesson(lesson).themas(themas).build();
                    courseTerminRepo.save(courseTermin);
                    lesson.addTermin(courseTermin);
                    lesson.setHasTermins(true);
                }
                lesson.getTermins().sort(Comparator.comparing(Termin::getDateTime));
                lessonRepository.save(lesson);
            }
        } else {
            lesson.setStudentsUpperBound(1);
            terminRepo.deleteAll(lesson.getTermins());
            lesson.removeAllTermins();
            if (courseRequest.getCourseTerminRequests() != null) {
                for (LessonTerminRequest privateLessonTermin : courseRequest.getPrivateLessonTermins()) {
                    Thema thema = null;
                    if (courseRequest.getThemas() != null) {
                        if (courseRequest.getThemas()[0].getDescription() == null) {
                            thema = Thema.builder().title(courseRequest.getThemas()[0].getTitle()).build();
                        } else {
                            thema = Thema.builder().description(courseRequest.getThemas()[0].getDescription())
                                    .title(courseRequest.getThemas()[0].getTitle()).build();
                        }
                        themaRepository.save(thema);
                    }
                    String hours = privateLessonTermin.getLessonHours();
                    LessonTermin lessonTermin = LessonTermin.builder().lessonHours(Integer.parseInt(hours.replace(":", "")))
                            .dateTime(Timestamp.valueOf(privateLessonTermin.getDate() + " " + hours + ":00")).thema(thema).build();
                    lessonTerminRepo.save(lessonTermin);
                    lessonTermin.setLesson(lesson);
                    lesson.addTermin(lessonTermin);
                    lesson.setHasTermins(true);
                }
                lesson.getTermins().sort(Comparator.comparing(Termin::getDateTime));
                lessonRepository.save(lesson);
            }
        }
        if (!isDraft) {
            if (lesson.isPrivateLesson()) addNewPriceLesson(lesson.getPrice());
            else addNewPriceCourse(lesson.getPrice());
        }
    }

    private String getDaysOfWeek(int[] days) throws CustomException {
        StringBuilder daysOfWeek = new StringBuilder();
        for (int i : days) {
            switch (i) {
                case 1 -> daysOfWeek.append("Ponedelnik, ");
                case 2 -> daysOfWeek.append("Vtornik, ");
                case 3 -> daysOfWeek.append("Srqda, ");
                case 4 -> daysOfWeek.append("Chetvurtuk, ");
                case 5 -> daysOfWeek.append("Petuk, ");
                case 6 -> daysOfWeek.append("Subota, ");
                case 7 -> daysOfWeek.append("Nedelq, ");
                default -> throw new CustomException(HttpStatus.BAD_REQUEST, "Days of the week are 1-7");
            }
        }
        return daysOfWeek.toString().substring(0, daysOfWeek.length() - 3);
    }

    private void checkForUnallowedChanges(Lesson lesson, CreateCourseRequest courseRequest) throws CustomException {
        boolean violations = courseRequest.getCourseTerminRequests().isEmpty() && lesson.getTermins().isEmpty()
                || lesson.getTitle().isEmpty() && courseRequest.getTitle().isEmpty()
//                || !EnumUtils.isValidEnum(Subject.class, lesson.getSubject()) && !EnumUtils.isValidEnum(Subject.class, courseRequest.getSubject())
                || lesson.getDescription().isEmpty() && courseRequest.getDescription().isEmpty()
//                || lesson.getImageLocation().isEmpty() && courseRequest.getImageLocation().isEmpty()
                || lesson.getPrice() == 0 && courseRequest.getPrice() == 0;
        if (violations) throw new CustomException(HttpStatus.BAD_REQUEST, "Please use the interface to send requests");
    }

    public void removeDraft(int lessonID) {
        var lesson = lessonRepository.getLessonByLessonID(lessonID);
        terminRepo.deleteAll(lesson.getTermins());
        lesson.getTeacher().removeLesson(lesson);
        lessonRepository.delete(lesson);
    }

    public void removeTermin(int terminID, int lessonID) throws CustomException {
        var termin = terminRepo.getTerminByTerminID(terminID);
        if (!termin.isEmpty()) throw new CustomException(HttpStatus.FORBIDDEN, "Login link");
        var lesson = lessonRepository.getLessonByLessonID(lessonID);
        lesson.removeTermin(termin);
        terminRepo.deleteById(terminID);
    }

    public HomePageResponse getHomePageInfo() throws CustomException {
        List<Lesson> lessons = lessonRepository.findTop12ByOrderByPopularityDesc();
        // Add file reader and links to the courses
        HomePageResponse homePageResponse = new HomePageResponse();

        List<LessonResponse> lessonResponses = new ArrayList<>();
        for (Lesson lesson : lessons) {
            List<CourseTermin> termins;
            List<LessonTermin> termins2;
            LessonResponse lessonResponse;
            if (!lesson.isPrivateLesson()) {
                termins = lesson.getCourseTermins();
                lessonResponse = new LessonResponse(lesson, termins.get(0).getDate(), termins.get(0).getTime(),
                        termins.get(0).getStudentsUpperBound() - termins.get(0).getPlacesRemaining());
                int weekLength = lesson.getCourseTermins().get(0).getWeekLength();
                String[] days = termins.get(0).getCourseDays().split(",");
                lessonResponse.setWeekLength(weekLength);
                lessonResponse.setPricePerHour(lesson.getPrice() / (days.length * weekLength));
            } else {
                termins2 = lesson.getLessonTermins();
                lessonResponse = new LessonResponse(lesson, termins2.get(0).getDate(), termins2.get(0).getTime(), 0);
                lessonResponse.setPricePerHour(lesson.getPrice());
            }
            lessonResponses.add(lessonResponse);
        }

        List<ReviewResponse> reviewResponses = new ArrayList<>();
        for (Review review : reviewRepo.getDistinct3ByOrderByRatingDescMessageDesc()) {
            ReviewResponse reviewResponse = new ReviewResponse(review);
            reviewResponses.add(reviewResponse);
        }

        homePageResponse.setPopularLessonsResponse(lessonResponses);
        homePageResponse.setReviewsResponse(reviewResponses);
        return homePageResponse;
    }

    public void leaveReview(String token, ReviewRequest reviewRequest) {
        //TODO Change from Teacher to Student!
        Lesson lesson = lessonRepository.getLessonByLessonID(reviewRequest.getLessonId());
        Teacher teacher = teacherRepository.findTeacherByTokens_token(token.substring(7));
        Review review = Review.builder().dateTime(Timestamp.valueOf(LocalDateTime.now())).lesson(lesson).message(reviewRequest.getMessage())
                .rating(reviewRequest.getRating()).studentName(teacher.getFirstname()).studentSurname(teacher.getLastname())
                .teacher(teacher).build();
        reviewRepo.save(review);
        lesson.leaveReview(review);
        lessonRepository.save(lesson);
        teacher.leaveReview(review);
        teacherRepository.save(teacher);
    }

    public FilterResponse getFilters(boolean isPrivateLesson) {
        List<String> subjects = lessonRepository.getAllSubjects();
        List<String> grades = lessonRepository.getAllGrades();
        double[] prices;
        if (isPrivateLesson) {
            prices = new double[]{BOTTOM20PRICE_LESSON, BOTTOM40PRICE_LESSON,
                    (LOWERMEDIAN_PRICE_LESSON + UPPERMEDIAN_PRICE_LESSON) / 2, BOTTOM60PRICE_LESSON, BOTTOM80PRICE_LESSON};
        } else {
            prices = new double[]{BOTTOM20PRICE_COURSE,
                    BOTTOM40PRICE_COURSE, (LOWERMEDIAN_PRICE_COURSE + UPPERMEDIAN_PRICE_COURSE) / 2, BOTTOM60PRICE_COURSE, BOTTOM80PRICE_COURSE};
        }
        Arrays.sort(prices);
        return new FilterResponse(subjects, grades, prices);
    }

    public FilterResponse getSubjectGrade() {
        List<String> subjects = new ArrayList<>();
        for (Subject subject : Subject.values()) {
            subjects.add(subject.toString());
        }
        List<String> grades = new ArrayList<>();
        for (Grade grade : Grade.values()) {
            grades.add(grade.toString());
        }
        FilterResponse filterResponse = new FilterResponse();
        filterResponse.setGrades(grades);
        filterResponse.setSubjects(subjects);
        return filterResponse;
    }

    public List<LessonResponse> getFilteredLessons(FilterRequest request) throws IllegalArgumentException, CustomException {
        List<LessonResponse> lessonResponses = new ArrayList<>();
        Pageable sortedAndPaged = PageRequest.of(request.getPageNumber() - 1, 12);
        String sort = request.getSort();
        if (sort == null) sort = "";
        switch (sort) {
            case "Най-евтини" -> sort = "c.lesson.price";
            case "Най-висок" -> sort = "c.lesson.rating";
            case "Най-скоро започващи" -> sort = "c.dateTime";
            default -> sort = "c.lesson.popularity";
        }
        if (request.getPriceLowerBound() >= 0 && request.getPriceUpperBound() == 0) {
            request.setPriceUpperBound(10000);
            request.setPriceLowerBound(0);
        }
        if (request.getHoursUpperBound() == 0) request.setHoursUpperBound(2400);
        if (request.getLowerBound() == null)
            request.setLowerBound(String.valueOf(Timestamp.valueOf(LocalDateTime.now())));
        else request.setLowerBound(request.getLowerBound() + " 00:00:00");
        if (request.getUpperBound() == null)
            request.setUpperBound(String.valueOf(new Timestamp(System.currentTimeMillis() + 31556926000L)));
        else request.setUpperBound(request.getUpperBound() + " 23:59:59");
        Page<Lesson> lessons;
        int weekLength = -1;
        if (request.isPrivateLesson()) {
            lessons = lessonTerminRepo.getFilteredLessonTermins(request.getSearchTerm(), request.getSearchTerm(), request.getSearchTerm(),
                    request.getSubject(), false, request.getGrade(), request.getPriceLowerBound(), request.getPriceUpperBound(),
                    request.getHoursLowerBound(), request.getHoursUpperBound(), Timestamp.valueOf(request.getLowerBound()),
                    Timestamp.valueOf(request.getUpperBound()), false, true, sortedAndPaged);
        } else {
            lessons = courseTerminRepo.getFilteredCourseTermins(request.getSearchTerm(), request.getSearchTerm(), request.getSearchTerm(),
                    request.getSubject(), false, request.getGrade(), request.getPriceLowerBound(), request.getPriceUpperBound(),
                    request.getHoursLowerBound(), request.getHoursUpperBound(), Timestamp.valueOf(request.getLowerBound()),
                    Timestamp.valueOf(request.getUpperBound()), false, false, sortedAndPaged);
            weekLength = 0;
        }
        for (Lesson lesson : lessons) {
            List<CourseTermin> termins;
            List<LessonTermin> termins2;
            LessonResponse lessonResponse;
            if (!lesson.isPrivateLesson()) {
                termins = lesson.getCourseTermins();
                lessonResponse = new LessonResponse(lesson, termins.get(0).getDate(), termins.get(0).getTime(),
                        termins.get(0).getStudentsUpperBound() - termins.get(0).getPlacesRemaining());
                lessonResponse.setWeekLength(lesson.getCourseTermins().get(0).getWeekLength());
                int weekLength1 = lesson.getCourseTermins().get(0).getWeekLength();
                String[] days = termins.get(0).getCourseDays().split(",");
                lessonResponse.setWeekLength(weekLength1);
                lessonResponse.setPricePerHour(lesson.getPrice() / (days.length * weekLength1));
            } else {
                termins2 = lesson.getLessonTermins();
                lessonResponse = new LessonResponse(lesson, termins2.get(0).getDate(), termins2.get(0).getTime(), 0);
                lessonResponse.setPricePerHour(lesson.getPrice());
            }
            lessonResponses.add(lessonResponse);
        }
        return lessonResponses;
    }

    public List<LessonResponse> getLessonById(int id) throws CustomException {
//TODO do lesson page for logged user   Teacher teacher = teacherRepository.findTeacherByTokens_token(token.substring(7));
        var lesson = lessonRepository.getLessonByLessonID(id);
        LessonResponse lessonResponse;
        List<ReviewResponse> reviews = getLessonReviews(id, "", 1);
        if (lesson.isPrivateLesson()) {
            List<LessonTermin> lessonTermins = lesson.getLessonTermins();
            List<LessonTerminResponse> lessonTerminResponses = new ArrayList<>();
            ThemaSimpleResponse thema = new ThemaSimpleResponse(lessonTermins.get(0).getThema().getTitle(), lessonTermins.get(0).getThema().getDescription());
            for (LessonTermin lessonTermin : lessonTermins) {
                Timestamp timestamp = Timestamp.valueOf(Instant.ofEpochMilli(lessonTermin.getDateTime().getTime()
                        + lesson.getLength() * 60000L).atZone(ZoneId.systemDefault()).toLocalDateTime());
                lessonTerminResponses.add(new LessonTerminResponse(lessonTermin.getTerminID(), lessonTermin.getDate(),
                        lessonTermin.getTime() + " - " + timestamp.toString().substring(11, 16)));
            }
            lessonResponse = new LessonResponse(lesson, lessonTerminResponses, reviews, thema);
            lessonResponse.setPricePerHour(lessonResponse.getPrice());
        } else {
            lessonResponse = new LessonResponse(lesson, reviews);
        }
        List<LessonResponse> lessonResponses = new ArrayList<>();
        lessonResponses.add(lessonResponse);
        for (Lesson lesson1 : lessonRepository.findTop4BySubjectOrGradeOrderByPopularityDesc(lesson.getSubject(), lessonResponse.getGrade())) {
            if (Objects.equals(lesson1.getLessonID(), lesson.getLessonID())) continue;
            if (lesson1.isPrivateLesson()) {
                List<LessonTermin> lessonTermins = lesson1.getLessonTermins();
                lessonResponses.add(new LessonResponse(lesson1, lessonTermins.get(0).getDate(), lessonTermins.get(0).getTime(), 0));
            } else {
                List<CourseTermin> courseTermins = lesson1.getCourseTermins();
                lessonResponses.add(new LessonResponse(lesson1, courseTermins.get(0).getDate(), courseTermins.get(0).getTime(),
                        courseTermins.get(0).getStudentsUpperBound() - courseTermins.get(0).getPlacesRemaining()));
                lessonResponse.setWeekLength(courseTermins.get(0).getWeekLength());
            }
        }
        return lessonResponses;
    }

    public ClassroomPageResponse getClassroomPage(String token, int terminId, boolean isPrivateLesson) throws CustomException {
        Teacher teacher = teacherRepository.findTeacherByTokens_token(token.substring(7));
        CourseTermin courseTermin;
        LessonTermin lessonTermin;
        Lesson lesson;
        List<ThemaResponse> themas = new ArrayList<>();
        List<UserProfileResponse> students = new ArrayList<>();
        ClassroomPageResponse classroomPageResponse;
        if (isPrivateLesson) {
            lessonTermin = lessonTerminRepo.getLessonTerminByTerminID(terminId);
            lesson = lessonTermin.getLesson();
            if (!Objects.equals(lesson.getTeacher().getId(), teacher.getId())) throw new CustomException(HttpStatus.CONFLICT, "Имате достъп само до вашите уроци");
            Thema thema = lessonTermin.getThema();
            ThemaResponse themaResponse = new ThemaResponse(thema.getThemaID(), thema.getLinkToRecording(), thema.getLinkToRecording(),
                    thema.getPresentation(), thema.getTitle(), thema.getDescription());
            themas.add(themaResponse);
            Student student = lessonTermin.getStudent();
            students.add(new UserProfileResponse(student.getId(), student.getFirstname(), student.getLastname()));
            //endTime
            Timestamp timestamp = Timestamp.valueOf(Instant.ofEpochMilli(lessonTermin.getDateTime().getTime()
                    + lesson.getLength() * 60000L).atZone(ZoneId.systemDefault()).toLocalDateTime());
            classroomPageResponse = ClassroomPageResponse.builder().lessonTitle(lesson.getTitle())
                    .lessonDescription(lesson.getDescription()).courseHours(lessonTermin.getTime() + " - " + timestamp.toString().substring(11, 16)
                    ).startDate(lessonTermin.getDate())
                    .themas(themas).courseTerminId(lessonTermin.getTerminID())
                    .enrolledStudents(1).students(students).build();
        }
        else {
            courseTermin = courseTerminRepo.getCourseTerminByTerminID(terminId);
            lesson = courseTermin.getLesson();
            if (!Objects.equals(lesson.getTeacher().getId(), teacher.getId())) throw new CustomException(HttpStatus.CONFLICT, "Имате достъп само до вашите уроци");
            for (Thema thema : courseTermin.getThemas()) {
                ThemaResponse themaResponse = new ThemaResponse(thema.getThemaID(), thema.getLinkToRecording(), thema.getLinkToRecording(),
                        thema.getPresentation(), thema.getTitle(), thema.getDescription());
                themas.add(themaResponse);
            }
            for (Student student :  courseTermin.getEnrolledStudents()) {
                students.add(new UserProfileResponse(student.getId(), student.getFirstname(), student.getLastname()));
            }
            String endDate = (new Timestamp(courseTermin.getDateTime().getTime() + (long) courseTermin.getWeekLength() * 7 * 86400000).toString()).substring(0, 10);
            //endTime
            Timestamp timestamp = Timestamp.valueOf(Instant.ofEpochMilli(courseTermin.getDateTime().getTime()
                    + lesson.getLength() * 60000L).atZone(ZoneId.systemDefault()).toLocalDateTime());
            classroomPageResponse = ClassroomPageResponse.builder().lessonTitle(lesson.getTitle())
                    .lessonDescription(lesson.getDescription()).courseHours(courseTermin.getTime() + " - " + timestamp.toString().substring(11, 16))
                    .startDate(courseTermin.getDate()).courseDays(courseTermin.getCourseDays())
                    .enrolledStudents(courseTermin.getStudentsUpperBound() - courseTermin.getPlacesRemaining()).endDate(endDate)
                    .themas(themas).courseTerminId(courseTermin.getTerminID()).students(students).build();
        }
        return classroomPageResponse;
    }

    public List<LessonResponse> getStudentAll(String token, LessonStatus lessonStatus, String sort) throws ClassCastException, CustomException {
        //TODO add paging? or make default lessonstatus to active
        Student student = studentRepository.findStudentByTokens_token(token.substring(7));

        if (sort.equals("Частни уроци")) return getStudentPrivateLessons(lessonStatus, student);
        else if (sort.equals("Курсове")) return getStudentCourses(lessonStatus, student);

        List<LessonResponse> lessonResponses = new ArrayList<>();
        List<LessonTermin> lessonTermins = student.getPrivateLessons();
        int counter = 0;
        int lessonsLength = lessonTermins.size() - 1;
        for (CourseTermin courseTermin : student.getCourses()) {
            LessonResponse lessonResponse;
            if (counter > lessonsLength) {
                if (!courseTermin.getLessonStatus().equals(lessonStatus)) continue;
                CourseTerminRequestResponse courseTerminRequestResponse = new CourseTerminRequestResponse(courseTermin);
                Lesson lesson = courseTermin.getLesson();
                Teacher teacher = lesson.getTeacher();
                lessonResponse = new LessonResponse(lesson.getLessonID(), lesson.getTitle(), lesson.isPrivateLesson(),
                        teacher.getFirstname(), teacher.getLastname(), courseTermin.getLessonStatus().toString(), courseTerminRequestResponse, teacher.getId());
                lessonResponses.add(lessonResponse);
                continue;
            }
            LessonTermin lessonTermin = lessonTermins.get(counter);
            if (courseTermin.getDateTime().before(lessonTermin.getDateTime())) {
                if (!courseTermin.getLessonStatus().equals(lessonStatus)) continue;
                CourseTerminRequestResponse courseTerminRequestResponse = new CourseTerminRequestResponse(courseTermin);
                Lesson lesson = courseTermin.getLesson();
                Teacher teacher = lesson.getTeacher();
                lessonResponse = new LessonResponse(lesson.getLessonID(), lesson.getTitle(), lesson.isPrivateLesson(),
                        teacher.getFirstname(), teacher.getLastname(), courseTermin.getLessonStatus().toString(), courseTerminRequestResponse, teacher.getId());
            } else {
                if (!lessonTermin.getLessonStatus().equals(lessonStatus)) continue;
                Lesson lesson = lessonTermin.getLesson();
                Teacher teacher = lesson.getTeacher();
                lessonResponse = new LessonResponse(lesson.getLessonID(), lesson.getTitle(), lesson.isPrivateLesson(),
                        teacher.getFirstname(), teacher.getLastname(), lessonTermin.getLessonStatus().toString(),
                        lessonTermin.getDate(), lessonTermin.getTime(), teacher.getId());
                counter++;
            }
            lessonResponses.add(lessonResponse);
        }
        while (counter <= lessonsLength) {
            LessonResponse lessonResponse;
            LessonTermin lessonTermin = lessonTermins.get(counter);
            if (!lessonTermin.getLessonStatus().equals(lessonStatus)) continue;
            Lesson lesson = lessonTermin.getLesson();
            Teacher teacher = lesson.getTeacher();
            lessonResponse = new LessonResponse(lesson.getLessonID(), lesson.getTitle(), lesson.isPrivateLesson(),
                    teacher.getFirstname(), teacher.getLastname(), lessonTermin.getLessonStatus().toString(),
                    lessonTermin.getDate(), lessonTermin.getTime(), teacher.getId());
            lessonResponses.add(lessonResponse);
            counter++;
        }
        return lessonResponses;
    }

    private List<LessonResponse> getStudentPrivateLessons(LessonStatus lessonStatus, Student student) {
        List<LessonResponse> lessonResponses = new ArrayList<>();
        for (LessonTermin lessonTermin : student.getPrivateLessons()) {
            if (!lessonTermin.getLessonStatus().equals(lessonStatus)) continue;
            Lesson lesson = lessonTermin.getLesson();
            Teacher teacher = lesson.getTeacher();
            LessonResponse lessonResponse = new LessonResponse(lesson.getLessonID(), lesson.getTitle(), lesson.isPrivateLesson(),
                    teacher.getFirstname(), teacher.getLastname(), lessonTermin.getLessonStatus().toString(),
                    lessonTermin.getDate(), lessonTermin.getTime(), teacher.getId());

            lessonResponses.add(lessonResponse);
        }
        return lessonResponses;
    }

    private List<LessonResponse> getStudentCourses(LessonStatus lessonStatus, Student student) {
        List<LessonResponse> lessonResponses = new ArrayList<>();
        for (CourseTermin courseTermin : student.getCourses()) {
            if (!courseTermin.getLessonStatus().equals(lessonStatus)) continue;
            CourseTerminRequestResponse courseTerminRequestResponse = new CourseTerminRequestResponse(courseTermin);
            Lesson lesson = courseTermin.getLesson();
            Teacher teacher = lesson.getTeacher();
            LessonResponse lessonResponse = new LessonResponse(lesson.getLessonID(), lesson.getTitle(), lesson.isPrivateLesson(),
                    teacher.getFirstname(), teacher.getLastname(), courseTermin.getLessonStatus().toString(), courseTerminRequestResponse, teacher.getId());
            lessonResponses.add(lessonResponse);
        }
        return lessonResponses;
    }

    public List<LessonResponse> getFavouriteCourses(String token, String sort, int pageNumber) throws ClassCastException, CustomException {
        Student student = studentRepository.findStudentByTokens_token(token.substring(7));
        List<LessonResponse> lessonResponses = new ArrayList<>();
        Pageable sortedAndPaged;
        switch (sort) {
            case "Най-популярни" ->
                    sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("popularity").descending());
            case "Най-скъпи" ->
                    sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("lesson_price").descending());
            case "Най-евтини" ->
                    sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("lesson_price").ascending());
            case "Най-висок рейтинг" ->
                    sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("lesson_rating").descending());
            case "Най-скоро започващи" ->
                    sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("dateTime").ascending());
            case "Най-нови" -> {
                List<Lesson> lessons = student.getFavouriteLessons();
                for (int i = (pageNumber - 1) * 12; i < pageNumber * 12; i++) {
                    Lesson lesson = lessons.get(i);
                    List<CourseTermin> termins;
                    List<LessonTermin> termins2;
                    LessonResponse lessonResponse;
                    if (!lesson.isPrivateLesson()) {
                        termins = lesson.getCourseTermins();
                        lessonResponse = new LessonResponse(lesson, termins.get(0).getDate(), termins.get(0).getTime(),
                                termins.get(0).getStudentsUpperBound() - termins.get(0).getPlacesRemaining());
                        lessonResponse.setWeekLength(lesson.getCourseTermins().get(0).getWeekLength());
                    } else {
                        termins2 = lesson.getLessonTermins();
                        lessonResponse = new LessonResponse(lesson, termins2.get(0).getDate(), termins2.get(0).getTime(), 0);
                    }
                    lessonResponses.add(lessonResponse);
                }
                return lessonResponses;
            }
            default -> sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("dateTime").ascending());
        }
        List<Lesson> lessons = lessonRepository.getLessonByisLikedByStudent_id(student.getId(), sortedAndPaged);
        for (Lesson lesson : lessons) {
            List<CourseTermin> termins;
            List<LessonTermin> termins2;
            LessonResponse lessonResponse;
            if (!lesson.isPrivateLesson()) {
                termins = lesson.getCourseTermins();
                lessonResponse = new LessonResponse(lesson, termins.get(0).getDate(), termins.get(0).getTime(),
                        termins.get(0).getStudentsUpperBound() - termins.get(0).getPlacesRemaining());
                lessonResponse.setWeekLength(lesson.getCourseTermins().get(0).getWeekLength());
            } else {
                termins2 = lesson.getLessonTermins();
                lessonResponse = new LessonResponse(lesson, termins2.get(0).getDate(), termins2.get(0).getTime(), 0);
            }
            lessonResponses.add(lessonResponse);
        }
        return lessonResponses;
    }

    public List<TeacherResponse> getFavouriteTeachers(String token) throws ClassCastException, CustomException {
        Student student = studentRepository.findStudentByTokens_token(token.substring(7));
        List<TeacherResponse> teachers = new ArrayList<>();
        for (Teacher teacher : student.getFavouriteTeachers()) {
            TeacherResponse teacherResponse = new TeacherResponse(teacher);
            teachers.add(teacherResponse);
        }
        return teachers;
    }

    public List<LessonResponse> getTeacherLessons(String token, String lessonStatus, boolean privateLessons, boolean upcoming) throws ClassCastException, CustomException {
        Teacher teacher = teacherRepository.findTeacherByTokens_token(token.substring(7));
        List<Lesson> lessons = teacher.getLessons();
        List<LessonResponse> lessonResponses = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (lessonStatus.equals("All")) {
                if (lesson.isDraft()) {
                    if (lesson.isHasTermins()) {
                        fillLessonResponseList(lessonResponses, lesson, "Draft");
                    } else {
                        LessonResponse lessonResponse = new LessonResponse(lesson, "", "", 0);
                        lessonResponse.setStatus("Draft");
                        lessonResponses.add(lessonResponse);
                    }
                }
                else if (lesson.isHasTermins()) fillLessonResponseList(lessonResponses, lesson, "Active");
                else {
                    LessonResponse lessonResponse = new LessonResponse(lesson, "", "", 0);
                    lessonResponse.setStatus("Inactive");
                    lessonResponses.add(lessonResponse);
                }
            }
            else if (upcoming) {
                if (lesson.isHasTermins()) {
                    fillLessonResponseList(lessonResponses, lesson, "Active");
                }
            } else if (lesson.isPrivateLesson() == privateLessons) {
                if (lessonStatus.equals("Draft")) {
                    if (!lesson.isDraft()) continue;
                    if (!lesson.isHasTermins()) {
                        LessonResponse lessonResponse = new LessonResponse(lesson, "", "", 0);
                        lessonResponse.setStatus("Draft");
                        lessonResponses.add(lessonResponse);
                    } else {
                        fillLessonResponseList(lessonResponses, lesson, "Draft");
                    }
                } else if (lessonStatus.equals("Inactive") && !lesson.isDraft()) {
                    if (lesson.isHasTermins()) continue;
                    LessonResponse lessonResponse = new LessonResponse(lesson, "", "", 0);
                    lessonResponse.setStatus("Inactive");
                    lessonResponses.add(lessonResponse);
                } else if (lessonStatus.equals("Active")) {
                    if (lesson.isHasTermins()) fillLessonResponseList(lessonResponses, lesson, "Active");
                } else
                    throw new CustomException(HttpStatus.BAD_REQUEST, "Моля изберете някой от предложените статуси през интерфейса");
            }
        }
        return lessonResponses;
    }

    private void fillLessonResponseList(List<LessonResponse> lessonResponses, Lesson lesson, String lessonStatus) throws CustomException {
        List<CourseTermin> termins;
        List<LessonTermin> termins2;
        LessonResponse lessonResponse;
        if (!lesson.isPrivateLesson()) {
            termins = lesson.getCourseTermins();
            lessonResponse = new LessonResponse(lesson, termins.get(0).getDate(), termins.get(0).getTime(),
                    termins.get(0).getStudentsUpperBound() - termins.get(0).getPlacesRemaining());
            lessonResponse.setWeekLength(lesson.getCourseTermins().get(0).getWeekLength());
            lessonResponse.setNumberOfTermins(termins.size());
        } else {
            termins2 = lesson.getLessonTermins();
            lessonResponse = new LessonResponse(lesson, termins2.get(0).getDate(), termins2.get(0).getTime(), 0);
            lessonResponse.setNumberOfTermins(termins2.size());
        }

        lessonResponse.setStatus(lessonStatus);
        lessonResponses.add(lessonResponse);
    }

    public List<CourseTerminRequestResponse> getCourseTerminsTeacher(String token, int lessonId) throws CustomException {
        // TODO add to teacher exclusive functions
        List<CourseTermin> courseTermins = courseTerminRepo.getCourseTerminsByLessonID(lessonId);
        List<CourseTerminRequestResponse> courseTerminRequestResponses = new ArrayList<>();
        for (CourseTermin courseTermin : courseTermins) {
            courseTerminRequestResponses.add(new CourseTerminRequestResponse(courseTermin, courseTermin.getLessonStatus()));
        }
        return courseTerminRequestResponses;
    }

    public List<LessonTerminResponse> getLessonTerminsTeacher(String token, int lessonId) throws ClassCastException, CustomException {
        // TODO add to teacher exclusive functions
        List<LessonTermin> lessonTermins = lessonTerminRepo.getLessonTerminsByLessonID(lessonId);
        List<LessonTerminResponse> lessonResponses = new ArrayList<>();
        int dayOfMonth = -1;
        int counter = 1;
        int lessonTerminsSize = lessonTermins.size();
        List<TimePair> timePairs = new ArrayList<>();
        for (LessonTermin lessonTermin : lessonTermins) {
            int currentDayOfMonth = lessonTermin.getDateTime().toLocalDateTime().getDayOfMonth();
            TimePair timePair = new TimePair(lessonTermin.getTime(), lessonTermin.isFull());
            timePairs.add(timePair);
            if (dayOfMonth != -1 && currentDayOfMonth != dayOfMonth) {
                dayOfMonth = currentDayOfMonth;
            } else if (currentDayOfMonth != dayOfMonth || counter == lessonTerminsSize) {
                LessonTerminResponse lessonTerminResponse = LessonTerminResponse.builder().date(lessonTermin.getDate()).times(timePairs)
                        .dayOfTheWeek(lessonTermin.getDateTime().toLocalDateTime().getDayOfWeek().toString()).status(lessonTermin.getLessonStatus().toString()).build();
                timePairs = new ArrayList<>();
                dayOfMonth = currentDayOfMonth;
                lessonResponses.add(lessonTerminResponse);
            }
            counter++;
        }
        return lessonResponses;
    }

    public List<ReviewResponse> getLessonReviews(int lessonId, String sort, int pageNumber) throws ClassCastException {
        Pageable sortedAndPaged;
        switch (sort) {
            case "Най-нови" -> sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("dateTime").descending());
            case "Най-стари" -> sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("dateTime").ascending());
            case "Най-висок" -> sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("rating").descending());
            case "Най-нисък" -> sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("rating").ascending());
            default -> sortedAndPaged = PageRequest.of(pageNumber - 1, 12, Sort.by("dateTime").descending());
        }
        List<Review> reviews = reviewRepo.getByLesson_lessonID(lessonId, sortedAndPaged);
        List<ReviewResponse> reviewResponses = new ArrayList<>();
        for (Review review : reviews) {
            reviewResponses.add(new ReviewResponse(review));
        }
        return reviewResponses;
    }

    public static void addNewPriceCourse(double newPrice) {
        COURSES_COUNT++;
        SUM_PRICE_COURSE += newPrice;
        LOWERMEDIAN_PRICE_COURSE = (COURSES_COUNT % 2 == 1) ? newPrice : ((LOWERMEDIAN_PRICE_COURSE + UPPERMEDIAN_PRICE_COURSE) / 2);
        UPPERMEDIAN_PRICE_COURSE = newPrice;
        double average = SUM_PRICE_COURSE / COURSES_COUNT;
        DEVIATION_COURSE = (COURSES_COUNT == 1) ? Math.abs(average - newPrice) : (DEVIATION_COURSE + Math.abs(average - newPrice))
                / (COURSES_COUNT - 1);
        BOTTOM20PRICE_COURSE = DEVIATION_COURSE * (-0.67) + average;
        BOTTOM40PRICE_COURSE = DEVIATION_COURSE * (-0.26) + average;
        BOTTOM60PRICE_COURSE = DEVIATION_COURSE * (0.26) + average;
        BOTTOM80PRICE_COURSE = DEVIATION_COURSE * (0.67) + average;
    }

    public static void addNewPriceLesson(double newPrice) {
        LESSONS_COUNT++;
        SUM_PRICE_LESSON += newPrice;
        LOWERMEDIAN_PRICE_LESSON = (LESSONS_COUNT % 2 == 1) ? newPrice : ((LOWERMEDIAN_PRICE_LESSON + UPPERMEDIAN_PRICE_LESSON) / 2);
        UPPERMEDIAN_PRICE_LESSON = newPrice;
        double average = SUM_PRICE_LESSON / LESSONS_COUNT;
        DEVIATION_LESSON = (LESSONS_COUNT == 1) ? Math.abs(average - newPrice) : (DEVIATION_LESSON + Math.abs(average - newPrice))
                / (LESSONS_COUNT - 1);
        BOTTOM20PRICE_LESSON = DEVIATION_LESSON * (-0.67) + average;
        BOTTOM40PRICE_LESSON = DEVIATION_LESSON * (-0.26) + average;
        BOTTOM60PRICE_LESSON = DEVIATION_LESSON * (0.26) + average;
        BOTTOM80PRICE_LESSON = DEVIATION_LESSON * (0.67) + average;
    }
}