# 02: Group Roster & Participants

**What to build:** The ability to navigate to a Group detail page, add Participant records to the roster, and designate one as `isSelf` (the operator).

**Blocked by:** 01: Group Creation & Listing

**Status:** resolved

- [x] Navigating to `/groups/{id}` displays the Group's details and an empty participant roster.
- [x] A user can submit a form on the Group page to add a new Participant by name.
- [x] A user can designate exactly one Participant in the Group as `isSelf = true`.
- [x] The roster displays all added Participants.

## Comments
- Implemented `Account` and `Participant` entities and relationship with `Group`.
- Implemented `ParticipantRepository` and `AccountRepository`.
- Implemented `GroupService.getGroup`, `GroupService.getParticipants`, `GroupService.addParticipant`, and `GroupService.designateSelf` enforcing single operator `isSelf` invariant per group.
- Implemented web layer: `GroupController` handling GET `/groups/{id}`, POST `/groups/{id}/participants`, and POST `/groups/{id}/participants/{participantId}/self`.
- Created Thymeleaf template `groups/show.html` displaying group details, participant roster, empty state, and participant creation form.
- Added comprehensive unit tests, `@DataJpaTest` repository tests, `@WebMvcTest` controller tests, and full `@SpringBootTest` integration tests.
