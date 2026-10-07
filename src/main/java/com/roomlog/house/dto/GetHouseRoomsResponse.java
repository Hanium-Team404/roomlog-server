package com.roomlog.house.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomlog.room.domain.Room;
import com.roomlog.scan.domain.Scan;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class GetHouseRoomsResponse {

    @JsonProperty("house_id")
    private final Long houseId;

    @JsonProperty("house_name")
    private final String houseName;

    private final List<RoomItem> rooms;

    @JsonProperty("total_count")
    private final int totalCount;

    private GetHouseRoomsResponse(Long houseId, String houseName, List<RoomItem> rooms) {
        this.houseId = houseId;
        this.houseName = houseName;
        this.rooms = rooms;
        this.totalCount = rooms.size();
    }

    public static GetHouseRoomsResponse of(Long houseId, String houseName, List<RoomItem> rooms) {
        return new GetHouseRoomsResponse(houseId, houseName, rooms);
    }

    @Getter
    public static class RoomItem {

        @JsonProperty("room_id")
        private final Long roomId;

        private final String name;

        @JsonProperty("ply_url")
        private final String plyUrl;

        @JsonProperty("thumbnail_url")
        private final String thumbnailUrl;

        @JsonProperty("latest_scan")
        private final LatestScanInfo latestScan;

        @JsonProperty("move_in_date")
        private final LocalDate moveInDate;

        /** 최신 스캔 생성 시각(초 단위). 같은 날 스캔한 방끼리도 이전/이후 순서를 가릴 수 있게 한다. 스캔이 없으면 null. */
        @JsonProperty("recent_scan_date")
        private final LocalDateTime recentScanDate;

        @JsonProperty("latest_scan_status")
        private final String latestScanStatus;

        private RoomItem(Long roomId, String name, String plyUrl, String thumbnailUrl,
                         LatestScanInfo latestScan, LocalDate moveInDate, LocalDateTime recentScanDate,
                         String latestScanStatus) {
            this.roomId = roomId;
            this.name = name;
            this.plyUrl = plyUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.latestScan = latestScan;
            this.moveInDate = moveInDate;
            this.recentScanDate = recentScanDate;
            this.latestScanStatus = latestScanStatus;
        }

        public static RoomItem of(Room room, Scan scan) {
            LatestScanInfo latestScanInfo = scan != null ? LatestScanInfo.from(scan) : null;
            LocalDateTime recentScanDate = scan != null ? scan.getCreatedAt() : null;
            String latestScanStatus = scan != null ? scan.getStatus().name() : null;
            return new RoomItem(room.getId(), room.getName(), room.getPlyUrl(), room.getThumbnailUrl(),
                    latestScanInfo, room.getMoveInDate(), recentScanDate, latestScanStatus);
        }

        @Getter
        public static class LatestScanInfo {

            @JsonProperty("scan_id")
            private final Long scanId;

            private LatestScanInfo(Long scanId) {
                this.scanId = scanId;
            }

            public static LatestScanInfo from(Scan scan) {
                return new LatestScanInfo(scan.getId());
            }
        }
    }
}
