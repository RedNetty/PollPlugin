package com.rednetty.poll;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class Poll {
    private final UUID pollUUID;
    private final UUID creatorUUID;
    private String question;
    private List<String> options = new ArrayList<>();
    private Map<UUID, String> votes = new HashMap<>(); // Player UUID -> chosen option
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean active;

    // Cached values for performance
    private transient String cachedCreatorName;
    private transient long lastCreatorNameUpdate = 0;
    private static final long CREATOR_NAME_CACHE_TIME = 300000; // 5 minutes

    public Poll(UUID pollUUID, UUID creatorUUID, String question) {
        if (pollUUID == null || creatorUUID == null || question == null) {
            throw new IllegalArgumentException("Poll UUID, creator UUID, and question cannot be null");
        }

        this.pollUUID = pollUUID;
        this.creatorUUID = creatorUUID;
        this.question = question.trim();
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public Poll(UUID pollUUID, UUID creatorUUID, String question, LocalDateTime createdAt, LocalDateTime expiresAt, boolean active) {
        if (pollUUID == null || creatorUUID == null || question == null) {
            throw new IllegalArgumentException("Poll UUID, creator UUID, and question cannot be null");
        }

        this.pollUUID = pollUUID;
        this.creatorUUID = creatorUUID;
        this.question = question.trim();
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.active = active;
    }

    // Getters and setters
    public UUID getPollUUID() {
        return pollUUID;
    }

    public UUID getCreatorUUID() {
        return creatorUUID;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Question cannot be null or empty");
        }
        this.question = question.trim();
    }

    public List<String> getOptions() {
        return new ArrayList<>(options);
    }

    public void setOptions(List<String> options) {
        if (options == null) {
            this.options = new ArrayList<>();
        } else {
            this.options = new ArrayList<>();
            for (String option : options) {
                if (option != null && !option.trim().isEmpty()) {
                    this.options.add(option.trim());
                }
            }
        }
    }

    public void addOption(String option) {
        if (option == null || option.trim().isEmpty()) {
            throw new IllegalArgumentException("Option cannot be null or empty");
        }

        String trimmedOption = option.trim();
        if (options.size() >= 6) {
            throw new IllegalStateException("Maximum 6 options allowed");
        }

        if (options.contains(trimmedOption)) {
            throw new IllegalArgumentException("Option already exists: " + trimmedOption);
        }

        this.options.add(trimmedOption);
    }

    public boolean removeOption(String option) {
        if (option == null) {
            return false;
        }

        boolean removed = options.remove(option.trim());

        // Remove votes for the removed option
        if (removed) {
            votes.values().removeIf(vote -> vote.equals(option.trim()));
        }

        return removed;
    }

    public Map<UUID, String> getVotes() {
        return new HashMap<>(votes);
    }

    public void setVotes(Map<UUID, String> votes) {
        if (votes == null) {
            this.votes = new HashMap<>();
        } else {
            this.votes = new HashMap<>(votes);
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        // Check both manual active flag and expiration time
        if (!active) {
            return false;
        }

        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            // Automatically mark as inactive if expired
            this.active = false;
            return false;
        }

        return true;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean hasVoted(UUID playerUUID) {
        return playerUUID != null && votes.containsKey(playerUUID);
    }

    public boolean vote(UUID playerUUID, String option) {
        if (playerUUID == null || option == null) {
            return false;
        }

        if (!isActive()) {
            return false;
        }

        String trimmedOption = option.trim();
        if (!options.contains(trimmedOption)) {
            return false;
        }

        votes.put(playerUUID, trimmedOption);
        return true;
    }

    public boolean removeVote(UUID playerUUID) {
        if (playerUUID == null || !isActive()) {
            return false;
        }

        return votes.remove(playerUUID) != null;
    }

    public String getPlayerVote(UUID playerUUID) {
        return playerUUID != null ? votes.get(playerUUID) : null;
    }

    public Map<String, Integer> getResults() {
        Map<String, Integer> results = new LinkedHashMap<>(); // Preserve order

        // Initialize all options with 0 votes
        for (String option : options) {
            results.put(option, 0);
        }

        // Count votes
        for (String vote : votes.values()) {
            if (options.contains(vote)) { // Ensure vote is still valid
                results.put(vote, results.getOrDefault(vote, 0) + 1);
            }
        }

        return results;
    }

    public String getWinningOption() {
        Map<String, Integer> results = getResults();
        return results.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public List<String> getTiedWinners() {
        Map<String, Integer> results = getResults();
        if (results.isEmpty()) {
            return new ArrayList<>();
        }

        int maxVotes = results.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : results.entrySet()) {
            if (entry.getValue() == maxVotes) {
                winners.add(entry.getKey());
            }
        }

        return winners;
    }

    public int getTotalVotes() {
        return votes.size();
    }

    public int getVotesForOption(String option) {
        if (option == null) {
            return 0;
        }

        return getResults().getOrDefault(option.trim(), 0);
    }

    public double getVotePercentage(String option) {
        if (option == null || getTotalVotes() == 0) {
            return 0.0;
        }

        int optionVotes = getVotesForOption(option);
        return (double) optionVotes / getTotalVotes() * 100.0;
    }

    public String getCreatorName() {
        // Use cached name if available and recent
        long now = System.currentTimeMillis();
        if (cachedCreatorName != null && (now - lastCreatorNameUpdate) < CREATOR_NAME_CACHE_TIME) {
            return cachedCreatorName;
        }

        // Try to get current name
        Player player = Bukkit.getPlayer(creatorUUID);
        if (player != null) {
            cachedCreatorName = player.getName();
            lastCreatorNameUpdate = now;
            return cachedCreatorName;
        }

        // Try to get offline player name
        try {
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(creatorUUID);
            String name = offlinePlayer.getName();
            if (name != null) {
                cachedCreatorName = name;
                lastCreatorNameUpdate = now;
                return cachedCreatorName;
            }
        } catch (Exception e) {
            // Ignore errors getting offline player name
        }

        // Return cached name if available, otherwise "Unknown"
        return cachedCreatorName != null ? cachedCreatorName : "Unknown";
    }

    public String getFormattedCreationDate() {
        try {
            return createdAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
        } catch (Exception e) {
            return "Unknown date";
        }
    }

    public String getFormattedExpirationDate() {
        if (expiresAt == null) {
            return "Never expires";
        }

        try {
            return expiresAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
        } catch (Exception e) {
            return "Unknown date";
        }
    }

    public String getTimeRemaining() {
        if (expiresAt == null) {
            return "Never expires";
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) {
            return "Expired";
        }

        try {
            long totalMinutes = ChronoUnit.MINUTES.between(now, expiresAt);

            if (totalMinutes < 1) {
                long seconds = ChronoUnit.SECONDS.between(now, expiresAt);
                return Math.max(0, seconds) + "s remaining";
            }

            long days = totalMinutes / (24 * 60);
            long hours = (totalMinutes % (24 * 60)) / 60;
            long minutes = totalMinutes % 60;

            if (days > 0) {
                return days + "d " + hours + "h remaining";
            } else if (hours > 0) {
                return hours + "h " + minutes + "m remaining";
            } else {
                return minutes + "m remaining";
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public long getTimeRemainingMillis() {
        if (expiresAt == null) {
            return Long.MAX_VALUE; // Never expires
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) {
            return 0; // Expired
        }

        try {
            return ChronoUnit.MILLIS.between(now, expiresAt);
        } catch (Exception e) {
            return 0;
        }
    }

    // MongoDB serialization methods
    public Document toDocument() {
        try {
            Document doc = new Document();
            doc.append("_id", pollUUID.toString());
            doc.append("creatorUUID", creatorUUID.toString());
            doc.append("question", question);
            doc.append("options", new ArrayList<>(options));
            doc.append("createdAt", createdAt.toString());
            doc.append("expiresAt", expiresAt != null ? expiresAt.toString() : null);
            doc.append("active", active);

            // Convert votes map to a list of documents for MongoDB
            List<Document> votesList = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : votes.entrySet()) {
                Document voteDoc = new Document();
                voteDoc.append("playerUUID", entry.getKey().toString());
                voteDoc.append("option", entry.getValue());
                votesList.add(voteDoc);
            }
            doc.append("votes", votesList);

            return doc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize poll to document", e);
        }
    }

    public static Poll fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }

        try {
            UUID pollUUID = UUID.fromString(doc.getString("_id"));
            UUID creatorUUID = UUID.fromString(doc.getString("creatorUUID"));
            String question = doc.getString("question");
            String createdAtStr = doc.getString("createdAt");
            LocalDateTime createdAt = createdAtStr != null ? LocalDateTime.parse(createdAtStr) : LocalDateTime.now();

            String expiresAtStr = doc.getString("expiresAt");
            LocalDateTime expiresAt = expiresAtStr != null ? LocalDateTime.parse(expiresAtStr) : null;

            boolean active = doc.getBoolean("active", true);

            Poll poll = new Poll(pollUUID, creatorUUID, question, createdAt, expiresAt, active);

            // Set options
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) doc.get("options");
            if (options != null) {
                poll.setOptions(options);
            }

            // Set votes
            @SuppressWarnings("unchecked")
            List<Document> votesList = (List<Document>) doc.get("votes");
            if (votesList != null) {
                Map<UUID, String> votes = new HashMap<>();
                for (Document voteDoc : votesList) {
                    try {
                        UUID playerUUID = UUID.fromString(voteDoc.getString("playerUUID"));
                        String option = voteDoc.getString("option");
                        if (playerUUID != null && option != null) {
                            votes.put(playerUUID, option);
                        }
                    } catch (Exception e) {
                        // Skip invalid vote entries
                    }
                }
                poll.setVotes(votes);
            }

            return poll;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize poll from document", e);
        }
    }

    // Utility methods
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Poll poll = (Poll) obj;
        return Objects.equals(pollUUID, poll.pollUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pollUUID);
    }

    @Override
    public String toString() {
        return "Poll{" +
                "uuid=" + pollUUID +
                ", question='" + question + '\'' +
                ", creator=" + getCreatorName() +
                ", active=" + isActive() +
                ", votes=" + getTotalVotes() +
                ", options=" + options.size() +
                '}';
    }

    /**
     * Validates the poll data
     */
    public boolean isValid() {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }

        if (options.isEmpty()) {
            return false;
        }

        if (options.size() > 6) {
            return false;
        }

        // Check for duplicate options
        Set<String> uniqueOptions = new HashSet<>(options);
        if (uniqueOptions.size() != options.size()) {
            return false;
        }

        for (String vote : votes.values()) {
            if (!options.contains(vote)) {
                return false;
            }
        }

        return true;
    }
}