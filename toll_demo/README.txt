톨게이트 구간 시연 - 다른 PC 설치

0) Python 3.10~3.12 설치 (python.org, 설치 때 'Add python.exe to PATH' 체크)
1) 처음 한 번 (인터넷 필요, 10~20분): setup_toll.bat 더블클릭 -> OK 가 나오면 성공
2) DB 주소 설정: db_url.txt 를 메모장으로 열어 한 줄만 적는다
   - 이 PC에 DB가 있을 때:   postgresql://postgres:1234@localhost:5432/scargo
   - 다른 PC(예: 원래 노트북)의 DB를 쓸 때: localhost 자리를 그 PC의 IP로 (예: 192.168.0.12)
     그 PC에서 PostgreSQL 이 외부 접속을 허용해야 함 (README 아래 참고)
2-1) DB(scargo)에 최종합본 DB 파일(7·8·9·10장 포함) 한 번 실행
3) 실행: run_toll.bat 더블클릭 -> 브라우저가 열림 (http://localhost:8001). 모델 로딩 10~30초
4) 종료: 검은 창에서 Ctrl+C

[DB를 다른 PC에 두는 경우 - DB가 있는 PC에서 한 번]
 a) postgresql.conf : listen_addresses = '*'
 b) pg_hba.conf 맨 아래 추가 : host  scargo  postgres  192.168.0.0/24  scram-sha-256
    (비밀번호 방식이 md5면 md5. 대역은 실제 IP에 맞게)
 c) PostgreSQL 서비스 재시작
 d) 방화벽 (관리자 cmd): netsh advfirewall firewall add rule name="pg5432" dir=in action=allow protocol=TCP localport=5432
[DB를 이 PC에 새로 만드는 경우] 기존 DB에서 pg_dump -U postgres scargo > scargo.sql 로 내보내고
 새 PC에서 createdb 후 psql 로 불러온 다음 seed_data.bat 는 필요 없음(덤프에 이미 포함)
