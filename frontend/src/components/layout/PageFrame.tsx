import { useEffect, type ReactNode } from 'react';
import type { Shard } from '../../api/types';
import { SiteFooter } from './SiteFooter';
import { Topbar } from './Topbar';

/** 탭 제목. 처음 열 때는 서버(app.html)가 넣어 둔 제목이고, 화면 안에서 넘어가면 여기서 바꾼다 */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title;
  }, [title]);
}

/** 첫 화면을 뺀 모든 화면의 틀: 상단 바 + 본문 + 공통 푸터 */
export function PageFrame({ shard, nickname, children }: { shard?: Shard; nickname?: string; children: ReactNode }) {
  return (
    <>
      <Topbar shard={shard} nickname={nickname} />
      {children}
      <SiteFooter />
    </>
  );
}
